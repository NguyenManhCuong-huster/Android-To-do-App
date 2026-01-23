package com.project3.todoapp.data.tasktag.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.project3.todoapp.data.tag.local.LocalTag
import com.project3.todoapp.data.task.local.LocalTask

@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE // Khi Task bị xóa, liên kết tự mất
        ),
        ForeignKey(
            entity = LocalTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE // Khi Tag bị xóa, liên kết tự mất
        )
    ],
    indices = [Index(value = ["tagId"])]
)

data class LocalTaskTagCrossRef(
    val taskId: String,
    val tagId: String,
    val modTime: Long
)