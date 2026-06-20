package com.project.hustassistant.data.ai


sealed interface AiChatItem {

    /** Placeholder hiển thị trong list khi AI đang xử lý (3 chấm nhảy). */
    data object Thinking : AiChatItem

    /**
     * Bong bóng assistant ĐANG stream — [content] cập nhật dần theo từng delta.
     * Khác [Message] ở chỗ: chưa lưu, chưa có reference/copy, render markdown thô +
     * con trỏ nhấp nháy. Khi stream xong sẽ được thay bằng [Message] hoàn chỉnh.
     */
    data class Streaming(val content: String) : AiChatItem

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

/**
 * 1 sự kiện trong luồng chat streaming (MỚI 2026-06). Repository phát ra dưới
 * dạng Flow; ViewModel ráp lại thành UI (bong bóng stream + card tool-call).
 */
sealed interface AiStreamEvent {
    /** 1 đoạn text mới của câu trả lời (nối thêm vào buffer hiện tại). */
    data class Delta(val text: String) : AiStreamEvent

    /** 1 tool vừa chạy xong (hiển thị card "AI đã làm gì"). */
    data class Tool(val toolCall: AiToolCall) : AiStreamEvent

    /**
     * Kết thúc thành công: [reply] là text ĐẦY ĐỦ (nguồn chính thống, có thể
     * khác buffer stream do dọn token), kèm [references] đã resolve.
     */
    data class Done(
        val reply: String,
        val references: List<AiReference>,
    ) : AiStreamEvent

    /** Lỗi giữa chừng. */
    data class Error(val message: String) : AiStreamEvent
}

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
