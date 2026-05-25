package com.project3.todoapp.data.tag.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocalTag — bản ghi Tag lưu trong Room (offline cache + source of truth cho UI).
 *
 * Hai cờ phục vụ đồng bộ offline → online:
 *  - [isDeleted]: soft-delete. UI/DAO sẽ ẩn (lọc isDeleted=0),
 *                 nhưng record vẫn còn để [DefaultTagRepository.sync] push lệnh xoá lên server.
 *                 Sau khi server xác nhận xoá, record mới bị hard-delete.
 *
 *  - [isDirty]:   record có thay đổi local chưa được push lên server.
 *                 Khi push thành công, repository set isDirty=false (markSynced).
 */
@Entity(tableName = "tag")
data class LocalTag(
    @PrimaryKey
    val id: String,
    val tagName: String,
    val colorHex: String,
    val modTime: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true,
)
