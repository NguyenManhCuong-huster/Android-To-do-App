package com.project3.todoapp.data

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.local.TaskDAO
import com.project3.todoapp.data.network.NetworkDataSource
import com.project3.todoapp.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        end: Long
    ): String {
        val taskId = withContext(dispatcher) {
            UUID.randomUUID().toString()
        }
        val createTime = System.currentTimeMillis()
        val task = Task(taskId, title, description, start = start, end = end, modTime = createTime)
        localDataSource.upsertTask(task.toLocal())
        saveTasksToNetwork()
        return taskId
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long
    ) {
        val modTime = System.currentTimeMillis()
        val task = getTask(taskId)?.copy(
            title = title,
            description = description,
            start = start,
            end = end,
            modTime = modTime
        ) ?: throw Exception("Task (id $taskId) not found")

        localDataSource.upsertTask(task.toLocal())
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
        return localDataSource.observeAll().map { tasks ->
            withContext(dispatcher) {
                tasks.toExternal()
            }
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
            val remoteTasks = networkDataSource.loadTasks().toExternal() // List<Task>
            if (remoteTasks.isEmpty()) return@withContext
            val localTasks = localDataSource.observeAll().first().toExternal() // List<Task>
            val localTasksMap = localTasks.associateBy { it.id }
            val tasksToUpsert = mutableListOf<Task>()

            //Save the task if it does not exist yet, or save the most recent edits if it does exist.
            for (remoteTask in remoteTasks) {
                val localTask = localTasksMap[remoteTask.id]

                if (localTask == null) {
                    tasksToUpsert.add(remoteTask)
                } else {
                    if (remoteTask.modTime > localTask.modTime) {
                        tasksToUpsert.add(remoteTask)
                    } else {
                        tasksToUpsert.add(localTask)
                    }
                }
            }

            localDataSource.syncTasks(tasksToUpsert.toLocal())
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