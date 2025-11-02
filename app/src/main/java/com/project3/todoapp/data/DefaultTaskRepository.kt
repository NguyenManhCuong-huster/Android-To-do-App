package com.project3.todoapp.data

import com.project3.todoapp.data.local.TaskDAO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class DefaultTaskRepository(
    val localDataSource: TaskDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TaskRepository {
    override suspend fun createTask(title: String, description: String, start: Long, end: Long) {
        val taskId = withContext(dispatcher) {
            UUID.randomUUID().toString()
        }
        val task = Task(taskId, title, description, start = start, end = end)
        localDataSource.upsertTask(task.toLocal())
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long
    ) {
        val task = getTask(taskId)?.copy(
            title = title,
            description = description,
            start = start,
            end = end
        ) ?: throw Exception("Task (id $taskId) not found")

        localDataSource.upsertTask(task.toLocal())
    }

    override suspend fun getTask(taskId: String): Task? {
        return localDataSource.getTaskById(taskId)?.toExternal()
    }

    override suspend fun deleteTask(taskId: String) {
        localDataSource.deleteById(taskId)
    }

    override suspend fun getTasks(): List<Task> {
        return withContext(dispatcher) {
            localDataSource.getAll().toExternal()
        }
    }

    override fun getTasksStream(): Flow<List<Task>> {
        return localDataSource.observeAll().map { tasks ->
            tasks.toExternal()
        }
    }

    override suspend fun deleteAllTasks() {
        localDataSource.deleteAll()
    }

    override suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean) {
        localDataSource.updateTaskCompletion(taskId, isCompleted)
    }
}