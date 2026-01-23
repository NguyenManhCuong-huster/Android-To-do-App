package com.project3.todoapp.data.task

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun createTask(
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority = Priority.MEDIUM,
        addressName: String? = null
    ): String

    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority,
        addressName: String?
    )

    suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean)

    suspend fun getTask(taskId: String): Task?

    suspend fun deleteTask(taskId: String)

    fun getTasksStream(): Flow<List<Task>>

    suspend fun getTasks(): List<Task>

    fun getTasksByTagStream(tagId: String): Flow<List<Task>>

    suspend fun deleteAllTasks()

    suspend fun sync()

}