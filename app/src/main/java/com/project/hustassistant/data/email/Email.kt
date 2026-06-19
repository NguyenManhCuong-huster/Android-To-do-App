package com.project.hustassistant.data.email

/**
 * Email — 1 row email trong UI list.
 * (Là message mới nhất trong thread → đại diện cho thread đó.)
 */
data class Email(
    val id: String,
    val accountId: String,
    val accountEmail: String?,
    val gmailMessageId: String?,
    val gmailThreadId: String?,
    val sender: String?,
    val subject: String?,
    val snippet: String?,
    val deepLinkIntent: String?,
    val receivedAt: String?,
    val modTime: Long,
    val isRead: Boolean = false,
)

/**
 * ThreadMessage — 1 message hiển thị trong màn đọc thread.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Thêm [localEmailId] (UUID của LocalEmail row). Cần để query attachments
 *    của message này từ AttachmentRepository (vì attachment.ownerId = LocalEmail.id,
 *    KHÔNG phải gmailMessageId).
 */
data class ThreadMessage(
    val localEmailId: String,
    val gmailMessageId: String,
    val sender: String?,
    val recipient: String?,
    val subject: String?,
    val bodyText: String,
    val bodyHtml: String,
    val snippet: String?,
    val receivedAt: String?,    // ISO string
    val deepLinkIntent: String?,
)
