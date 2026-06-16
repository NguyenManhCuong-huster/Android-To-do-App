package com.project.hustassistant.data.grade.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocalGrade — bản ghi điểm học phần lưu trong Room (offline cache + source of truth cho UI).
 *
 * Hai cờ phục vụ đồng bộ offline → online (giống LocalTag):
 *  - [isDeleted]: soft-delete. UI/DAO ẩn (lọc isDeleted=0), record vẫn còn để
 *                 sync push lệnh xoá lên server. Sau khi server xác nhận → hard-delete.
 *  - [isDirty]:   record có thay đổi local chưa push. Push xong → markSynced (isDirty=0).
 */
@Entity(tableName = "grade")
data class LocalGrade(
    @PrimaryKey
    val id: String,
    val semester: String,
    val courseCode: String,
    val courseName: String,
    val courseNameEn: String,
    val credits: Int,
    val letterGrade: String,
    val modTime: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true,
)
