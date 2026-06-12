package com.project3.todoapp.data.ai

/**
 * AiToolCall — model DOMAIN trung tính cho 1 lần AI gọi tool.
 *
 * Refactor Giai đoạn 1 (2026-06): tách concern.
 *   AiToolCallDto (wire)
 *        │  AiToolCallMapper.map()          (data: đọc shape result → kiểu)
 *        ▼
 *   AiToolCall (domain)  ← KHÔNG chứa chuỗi UI / i18n / format ngày
 *        │  AiToolCallPresenter.present()   (ui: sinh title/subtitle/icon)
 *        ▼
 *   AiChatItem.ToolCall (UI model cho adapter)
 *
 * Trước refactor: toàn bộ việc parse Map + dựng chuỗi tiếng Việt nằm trong
 * AiRepository — trộn tầng data với presentation. Giờ data chỉ tạo ra dữ liệu
 * có cấu trúc; mọi chữ hiển thị nằm ở Presenter (tầng ui).
 */
data class AiToolCall(
    val name: String,
    val success: Boolean,
    val error: String? = null,
    val payload: Payload? = null,
) {

    /** Dữ liệu đã bóc tách theo từng loại tool. Không có chữ hiển thị. */
    sealed interface Payload {

        data class TaskCreated(
            val title: String,
            val taskType: TaskKind,
            val endTimeIso: String?,
            val tags: List<TagRef>,
        ) : Payload

        data class WeeklyTasksCreated(
            val title: String,
            val created: Int,
            val skipped: Int,
            val taskType: TaskKind,
            val dayOfWeek: Int,            // 1=T2 … 7=CN, 0=không rõ
            val firstStartIso: String?,
            val lastStartIso: String?,
            val firstEndIso: String?,
            val lastEndIso: String?,
            val tags: List<TagRef>,
        ) : Payload

        data class AttachmentRead(
            val fileName: String?,
            val truncated: Boolean,
            val fromCache: Boolean,
        ) : Payload

        data class AttachmentReadFailed(
            val availableFiles: List<String>,
        ) : Payload

        data class EmailsFound(
            val count: Int,
            val query: String?,
        ) : Payload

        data class EmailRead(
            val subject: String?,
            val from: String?,
            val truncated: Boolean,
        ) : Payload

        data class EmailThreadRead(
            val messageCount: Int,
        ) : Payload

        data class NewsFound(
            val count: Int,
            val query: String?,
            val kind: NewsKind,
        ) : Payload

        data class NewsRead(
            val title: String?,
            val kind: NewsKind,
            val tag: String?,
            val truncated: Boolean,
        ) : Payload

        data class WebSearched(
            val query: String?,
            val count: Int,
        ) : Payload
    }

    /** Loại task (raw từ server). */
    enum class TaskKind {
        CLASS, EXAM, TODO;

        companion object {
            fun from(raw: String?): TaskKind = when (raw?.uppercase()) {
                "CLASS" -> CLASS
                "EXAM" -> EXAM
                else -> TODO
            }
        }
    }

    /** Loại news (raw). UNKNOWN khi server không nói rõ. */
    enum class NewsKind {
        NEWS, PLAN, UNKNOWN;

        companion object {
            fun from(raw: String?): NewsKind = when (raw?.uppercase()) {
                "NEWS" -> NEWS
                "PLAN" -> PLAN
                else -> UNKNOWN
            }
        }
    }

    data class TagRef(val name: String, val colorHex: String?)
}
