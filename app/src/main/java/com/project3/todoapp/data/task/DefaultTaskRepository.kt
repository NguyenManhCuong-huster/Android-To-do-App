package com.project3.todoapp.data.task

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.task.local.TaskDAO
import com.project3.todoapp.data.task.network.NetworkDataSource
import com.project3.todoapp.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DefaultTaskRepository(
    private val networkDataSource: NetworkDataSource,
    private val localDataSource: TaskDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager
) : TaskRepository {

    val isNetworkEnable = true

    override suspend fun createTask(
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority,
        addressName: String?
    ): String {
        val taskId = withContext(dispatcher) {
            UUID.randomUUID().toString()
        }
        val createTime = System.currentTimeMillis()

        val task = Task(
            id = taskId,
            title = title,
            description = description,
            start = start,
            end = end,
            modTime = createTime,
            priority = priority,
            addressName = addressName,
            isCompleted = false,
            latitude = null,
            longitude = null
        )

        localDataSource.upsertTask(task.toLocal())
        saveTasksToNetwork()
        return taskId
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority,
        addressName: String?
    ) {
        val modTime = System.currentTimeMillis()

        val oldTask = getTask(taskId) ?: throw Exception("Task (id $taskId) not found")

        val updatedTask = oldTask.copy(
            title = title,
            description = description,
            start = start,
            end = end,
            priority = priority,
            addressName = addressName,
            modTime = modTime
        )

        localDataSource.upsertTask(updatedTask.toLocal())
        saveTasksToNetwork()
    }

    override suspend fun getTask(taskId: String): Task? {
        return localDataSource.getTaskById(taskId)?.toExternal()
    }

    override suspend fun deleteTask(taskId: String) {
        localDataSource.deleteById(taskId)
        saveTasksToNetwork()
    }

    override suspend fun getTasks(): List<Task> {
        refresh()
        return withContext(dispatcher) {
            localDataSource.getAll().toExternal()
        }
    }

    override fun getTasksStream(): Flow<List<Task>> {
        refresh()
        return localDataSource.observeTasksWithTags().map { tasksWithTags ->
            withContext(dispatcher) {
                tasksWithTags.toExternal()
            }
        }
    }

    override fun getTasksByTagStream(tagId: String): Flow<List<Task>> {
        return localDataSource.getTasksForTag(tagId).map { tasks ->
            tasks.toExternal()
        }
    }

    override suspend fun deleteAllTasks() {
        localDataSource.deleteAll()
        saveTasksToNetwork()
    }

    override suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean) {
        localDataSource.updateTaskCompletion(taskId, isCompleted)
        saveTasksToNetwork()
    }

    private fun shouldSync(): Boolean {
        val isNetworkAvailable = networkManager.isOnline()
        val isUserLoggedIn = authManager.isUserLoggedIn()
        return isNetworkEnable && isNetworkAvailable && isUserLoggedIn
    }

    fun refresh() {
        scope.launch {
            if (shouldSync()) {
                try {
                    sync()
                } catch (e: Exception) {
                    Log.e("Repository", "Background sync failed", e)
                }
            }
        }
    }

    override suspend fun sync() {
        withContext(dispatcher) {
            val remoteTasks = networkDataSource.loadTasks().toExternal()
            val localTasks = localDataSource.getAll().toExternal()

            if (remoteTasks.isEmpty()) return@withContext

            // Last write win
            val mergedTasksMap = localTasks.associateBy { it.id }.toMutableMap()
            for (remoteTask in remoteTasks) {
                val localTask = mergedTasksMap[remoteTask.id]

                if (localTask == null) {
                    mergedTasksMap[remoteTask.id] = remoteTask
                } else {
                    if (remoteTask.modTime > localTask.modTime) {
                        mergedTasksMap[remoteTask.id] = remoteTask
                    }
                }
            }

            val finalTasks = mergedTasksMap.values.toList()
            localDataSource.syncTasks(finalTasks.toLocal())

            saveTasksToNetwork()
        }
    }

    private fun saveTasksToNetwork() {
        if (!shouldSync()) return

        scope.launch {
            try {
                val localTasks = localDataSource.getAll()
                val networkTasks = withContext(dispatcher) {
                    localTasks.toExternal().toNetwork()
                }
                networkDataSource.saveTasks(networkTasks)
            } catch (e: Exception) {
                // In a real app you'd handle the exception e.g. by exposing a `networkStatus` flow
                // to an app level UI state holder which could then display a Toast message.
            }
        }
    }
}