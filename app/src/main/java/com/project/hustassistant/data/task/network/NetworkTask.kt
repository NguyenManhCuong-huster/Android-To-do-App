package com.project.hustassistant.data.task.network

/**
 * NetworkTask — DTO trung gian giữa repository và Retrofit layer (TaskApi/TaskDto).
 *
 * Tách khỏi [TaskDto] (raw từ JSON server) để repository không phụ thuộc tên field
 * của API, dễ test bằng fake data source.
 *
 * ⚠️ Field [isDeleted] cho phép client phân biệt task tombstone từ server
 *  (server soft-delete) → repository hard-delete tương ứng ở local.
 */
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
    val addressName: String?,
    val isDeleted: Boolean = false,
)
