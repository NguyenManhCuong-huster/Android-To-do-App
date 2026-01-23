package com.project3.todoapp.data.tag.local

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "tag")
data class LocalTag(
    @PrimaryKey
    val id: String,
    val tagName: String,
    val colorHex: String,
    val modTime: Long
)