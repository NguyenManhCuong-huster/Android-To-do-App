package com.project3.todoapp.data.task.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocalTask — bản ghi Task lưu trong Room (offline cache + source of truth cho UI).
 *
 * Hai cờ phục vụ đồng bộ offline → online (giống LocalTag):
 *  - [isDeleted]: soft-delete. UI/DAO sẽ ẩn (lọc isDeleted=0),
 *                 nhưng record vẫn còn để [DefaultTaskRepository.sync] push lệnh xoá lên server.
 *                 Sau khi server xác nhận xoá, record mới bị hard-delete.
 *
 *  - [isDirty]:   record có thay đổi local chưa được push lên server.
 *                 Khi push thành công, repository set isDirty=false (markSynced).
 */
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
    val addressName: String?,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true,
)
