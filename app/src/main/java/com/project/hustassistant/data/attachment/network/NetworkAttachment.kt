package com.project.hustassistant.data.attachment.network

/**
 * NetworkAttachment — model trung gian giữa Retrofit DTO và Repository/Room.
 * Tách khỏi [AttachmentDto] (raw JSON) để repository không phụ thuộc tên field.
 *
 * Cùng pattern với NetworkEmail / NetworkNews.
 */
data class NetworkAttachment(
    val id: String,
    val ownerType: String,
    val ownerId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isDownloaded: Boolean,
    val isInline: Boolean,
    val sourceUrl: String?,
    val modTime: Long,
)
