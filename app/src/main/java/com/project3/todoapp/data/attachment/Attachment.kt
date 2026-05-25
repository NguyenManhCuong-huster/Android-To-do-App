package com.project3.todoapp.data.attachment

/**
 * Attachment — model UI cho 1 file đính kèm (email Gmail hoặc news HUST CTT).
 *
 * Polymorphic theo [ownerType]:
 *  - OWNER_EMAIL → ownerId là id của bảng emails (Local + Server).
 *  - OWNER_NEWS  → ownerId là id của bảng news.
 *
 * Client KHÔNG tạo/sửa attachments — chỉ sync metadata + download bytes on-demand.
 *
 * @property isDownloaded  Server đã có file trên đĩa chưa. Nếu false → tap chip
 *                         sẽ trả lỗi (file quá lớn / fetch fail) và UI hiển thị
 *                         disabled/grayed.
 * @property localCachedPath Đường dẫn file đã cache xuống device sau khi user
 *                           tap. null = chưa tải về máy. Khi != null → mở luôn
 *                           không gọi network.
 */
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
    NEWS;

    companion object {
        fun fromServer(value: String?): AttachmentOwner = when (value?.uppercase()) {
            "NEWS" -> NEWS
            else   -> EMAIL
        }
    }
}
