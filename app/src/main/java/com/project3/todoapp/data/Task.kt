package com.project3.todoapp.data


data class Task(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val start: Long,
    val end: Long
)