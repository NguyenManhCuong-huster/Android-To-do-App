package com.project.hustassistant.data.attachment.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LocalAttachment — Room entity cache metadata 1 file đính kèm.
 *
 * Bytes file KHÔNG lưu trong Room. Thay vào đó:
 *   • [localCachedPath]: nếu user đã tap để mở file → AttachmentRepository
 *     stream từ server xuống cache dir và update field này.
 *   • Nếu null → tap chip sẽ trigger download.
 *
 * Index quan trọng:
 *   • (ownerType, ownerId) — query attachments của 1 email/news cụ thể (rất
 *     thường xuyên — render mỗi message trong thread reader, mỗi news detail).
 */
@Entity(
    tableName = "attachment",
    indices = [
        Index(value = ["ownerType", "ownerId"]),
        Index(value = ["modTime"]),
    ],
)
data class LocalAttachment(
    @PrimaryKey
    val id: String,
    val ownerType: String,         // 'EMAIL' | 'NEWS' (string thay vì enum để khỏi cần TypeConverter)
    val ownerId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isDownloaded: Boolean,     // server-side
    val isInline: Boolean,
    val sourceUrl: String?,
    val modTime: Long,
    val localCachedPath: String?,  // client-side cache path
)
