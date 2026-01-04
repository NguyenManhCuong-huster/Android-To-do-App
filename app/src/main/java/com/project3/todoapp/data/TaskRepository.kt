package com.project3.todoapp.data

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun createTask(title: String, description: String, start: Long, end: Long): String

    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long
    )

    suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean)

    suspend fun getTask(taskId: String): Task?

    suspend fun deleteTask(taskId: String)

    fun getTasksStream(): Flow<List<Task>>

    suspend fun getTasks(): List<Task>

    suspend fun deleteAllTasks()

    suspend fun sync()

}