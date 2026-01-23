package com.project3.todoapp.data.tasktag

data class TaskTagCrossRef(
    val taskId: String,
    val tagId: String,
    val modTime: Long
)