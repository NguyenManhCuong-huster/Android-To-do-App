package com.project3.todoapp.data.ai.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LocalChatMessage — 1 tin nhắn (user/assistant) thuộc 1 phiên chat. MỚI 2026-06.
 *
 * `seq` giữ thứ tự hiển thị (tăng dần trong phiên) — bền vững qua restart, không
 * phụ thuộc timestamp. references/attachments lưu JSON (Gson) trong repository.
 *
 * Tool-call card KHÔNG được lưu (chỉ là UI tạm của lượt live); khi resume chỉ
 * dựng lại bubble user/assistant — đúng với cách history gửi cho server.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = LocalChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class LocalChatMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val seq: Long,
    val role: String,             // "USER" | "ASSISTANT"
    val content: String,
    val referencesJson: String?,  // JSON List<AiReference> hoặc null
    val attachmentsJson: String?, // JSON List<AttachmentRef> hoặc null
    val createdAt: Long,
)
