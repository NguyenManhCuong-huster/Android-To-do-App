package com.project3.todoapp.data.task.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task")
data class LocalTask(
    @PrimaryKey val id: String,
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
