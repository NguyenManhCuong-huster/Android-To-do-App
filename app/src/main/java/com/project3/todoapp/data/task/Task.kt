package com.project3.todoapp.data.task

import com.project3.todoapp.data.tag.Tag

data class Task(
    val id: String,
    val title: String,
    val description: String,
    var isCompleted: Boolean = false,
    val start: Long,
    val end: Long,
    val modTime: Long,
    val priority: Priority = Priority.MEDIUM,

    // Location for Google Map
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressName: String? = null,

    val tags: List<Tag> = emptyList()
)