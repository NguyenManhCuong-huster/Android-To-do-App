package com.project3.todoapp.data.task.network

data class NetworkTask(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val start: Long,
    val end: Long,
    val modTime: Long,
    val priority: Int,
    val latitude: Double?,
    val longitude: Double?,
    val addressName: String?
)