package com.project.hustassistant.data.email.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LocalEmail — bản ghi 1 message Gmail trong Room cache.
 *
 * THAY ĐỔI 2025-05-02:
 *  - 1 row = 1 message. Lưu kèm recipient + body_text + body_html.
 *  - Thread query: filter theo gmailThreadId + receivedAt.
 *
 *  Read-only từ phía UI — chỉ sync ghi.
 */
@Entity(
    tableName = "email",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["receivedAt"]),
        Index(value = ["gmailThreadId", "receivedAt"]),    // composite cho thread query
    ],
)
data class LocalEmail(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val accountEmail: String?,
    val gmailMessageId: String?,
    val gmailThreadId: String?,
    val sender: String?,
    val recipient: String?,
    val subject: String?,
    val snippet: String?,
    val bodyText: String,
    val bodyHtml: String,
    val deepLinkIntent: String?,
    val receivedAt: Long,        // epoch millis
    val modTime: Long,
)
