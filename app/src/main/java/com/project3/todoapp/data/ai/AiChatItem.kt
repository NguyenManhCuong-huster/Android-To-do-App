package com.project3.todoapp.data.ai

/**
 * AiChatItem — 1 item hiển thị trong UI list của AI chat.
 *
 *   - [Message]  → bubble user/assistant (mở rộng từ [AiMessage]).
 *   - [ToolCall] → card "AI đã làm gì" (vd tạo task), chèn giữa các bubble.
 *
 * Server trả `tool_calls` array trong response → ViewModel render tool calls
 * TRƯỚC rồi tới reply của assistant, đúng thứ tự AI thực hiện.
 *
 * LƯU Ý: Khi gửi history lên server cho lần chat kế tiếp, CHỈ gửi
 * [Message] items (filter bỏ ToolCall). Server tự quản function-call loop
 * trong từng request — client không cần lưu tool history.
 */
sealed interface AiChatItem {

    data class Message(val message: AiMessage) : AiChatItem {
        val role: AiMessage.Role get() = message.role
        val content: String      get() = message.content
    }

    /**
     * Card "AI đã làm gì".
     *
     * @param name          Tên tool, hiện chỉ "create_task".
     * @param success       true nếu tool chạy thành công.
     * @param title         Tiêu đề card. VD: "Đã tạo task: Nộp báo cáo môn AI".
     * @param subtitle      Dòng phụ — meta (loại, deadline…). Có thể null.
     * @param tags          Tag đã được gán vào task (để render chip màu).
     * @param errorMessage  Khi success=false thì giải thích lỗi.
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

/** Wrapper trả về cho repository — reply text + tool call summary. */
data class AiChatResult(
    val reply: String,
    val toolCalls: List<AiChatItem.ToolCall>,
)
