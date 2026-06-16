package com.project.hustassistant.data.news.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LocalNews — bản ghi News trong Room (offline cache).
 *
 * KHÁC LocalTask:
 *  - Không có [isDirty]/[isDeleted] vì client KHÔNG tạo/sửa/xoá news. Read-only.
 *  - [DefaultNewsRepository.refresh] sync 1 chiều: xoá all rồi upsert all theo
 *    response server. Nếu admin xoá 1 news ở server → lần refresh sau sẽ tự biến mất
 *    ở local (vì không có trong response).
 *
 * @property kind 'NEWS' | 'PLAN' (lưu string thay vì enum để Room không cần TypeConverter).
 */
@Entity(
    tableName = "news",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["tag"]),
        Index(value = ["publishedAt"]),
    ],
)
data class LocalNews(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val summary: String?,
    val articleUrl: String?,
    val imageUrl: String?,
    val tag: String?,
    val publishedAt: Long,
    val modTime: Long,
    val sourceName: String?,
)
