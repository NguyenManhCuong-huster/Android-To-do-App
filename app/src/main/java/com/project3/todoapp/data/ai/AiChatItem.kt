package com.project3.todoapp.data.ai


sealed interface AiChatItem {

    data class Message(val message: AiMessage) : AiChatItem {
        val role: AiMessage.Role get() = message.role
        val content: String get() = message.content
        val attachments: List<AttachmentRef> get() = message.attachments
        val references: List<AiReference> get() = message.references   // ← MỚI 2026-06
    }

    /**
     * Card "AI đã làm gì".
     */
    data class ToolCall(
        val name: String,
        val success: Boolean,
        val title: String,
        val subtitle: String? = null,
        val tags: List<TagChip> = emptyList(),
        val errorMessage: String? = null,
    ) : AiChatItem {
        data class TagChip(
            val name: String,
            val colorHex: String?,
        )
    }
}

/** Wrapper trả về cho repository — reply text + tool call summary + references. */
data class AiChatResult(
    val reply: String,
    val toolCalls: List<AiChatItem.ToolCall>,
    val references: List<AiReference> = emptyList(),   // ← MỚI 2026-06
)

/**
 * AttachmentRef — UUID + tên file kèm mime/size để render chip trong bubble.
 *
 * Wire format (server ↔ client):
 *   { "id": "<uuid>", "file_name": "report.pdf",
 *     "mime_type": "application/pdf", "size_bytes": 12345 }
 *
 * UUID CHỈ tồn tại ở wire format và UI internal — KHÔNG bao giờ lộ ra cho AI
 * thấy. AI chỉ thấy tên file qua system instruction (do server build).
 */
data class AttachmentRef(
    val id: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

/**
 * AiReference — MỚI 2026-06.
 *
 * 1 trích dẫn email / tin tức trong reply của AI. Ứng với token
 * [[email:id]] / [[news:id]] còn lại trong reply text. AiMessageAdapter dùng
 * [label] để render chip bấm được; tap → mở email/news theo [id].
 *
 *   type  = "email" | "news"
 *   id    = UUID của email / news (đã được server xác thực quyền + tồn tại)
 *   label = subject (email) hoặc title (news) để hiển thị
 */
data class AiReference(
    val type: String,
    val id: String,
    val label: String,
)
