package com.project3.todoapp.data.attachment


data class Attachment(
    val id: String,
    val ownerType: AttachmentOwner,
    val ownerId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isDownloaded: Boolean,        // server-side: đã có trên đĩa server
    val isInline: Boolean,
    val sourceUrl: String?,           // URL gốc (chỉ NEWS)
    val modTime: Long,
    val localCachedPath: String?,     // path absolute trong cache dir của app
)

enum class AttachmentOwner {
    EMAIL,
    NEWS,
    AI_CHAT;                                                                  // ← MỚI 2026-05-31

    companion object {
        fun fromServer(value: String?): AttachmentOwner = when (value?.uppercase()) {
            "NEWS" -> NEWS
            "AI_CHAT" -> AI_CHAT
            else -> EMAIL
        }
    }
}
