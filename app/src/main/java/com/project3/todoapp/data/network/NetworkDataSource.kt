package com.project3.todoapp.data.network

interface NetworkDataSource {
    suspend fun loadTasks(): List<NetworkTask>

    suspend fun saveTasks(tasks: List<NetworkTask>)
}