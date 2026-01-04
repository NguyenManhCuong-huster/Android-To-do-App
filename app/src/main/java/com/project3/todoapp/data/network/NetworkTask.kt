package com.project3.todoapp.data.network

data class NetworkTask(
    val id: String,
    var title: String,
    var description: String,
    var isCompleted: Boolean,
    var start: Long,
    var end: Long,
    var modTime: Long
)