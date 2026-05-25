package com.project3.todoapp.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.ai.AiChatItem
import com.project3.todoapp.data.ai.AiMessage
import com.project3.todoapp.data.ai.AiRepository
import com.project3.todoapp.data.email.EmailRepository
import com.project3.todoapp.data.email.ThreadMessage
import com.project3.todoapp.data.news.News
import com.project3.todoapp.data.news.NewsKind
import com.project3.todoapp.data.news.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AiChatViewModel — chat với Gemini về 1 ChatContext (Email / News / Standalone).
 *
 * THAY ĐỔI 2026-05-08:
 *   - Gộp 2 VM (cũ: cho email + cho news) thành 1 universal VM.
 *   - Cả Email và News bootstrap với 1 USER message ban đầu chứa nội dung
 *     đối tượng đang xem → AI reply gợi ý hỏi gì.
 *   - Standalone: KHÔNG seed gì, lịch sử trống — user gõ tin đầu tiên.
 *   - Email vẫn dùng emailChat() (server tự fetch thread). News + Standalone
 *     dùng chat() vì server không cần biết context riêng.
 */
class AiChatViewModel(
    private val context: ChatContext,
    private val aiRepository: AiRepository,
    private val emailRepository: EmailRepository,
    private val newsRepository: NewsRepository,
) : ViewModel() {

    data class UiState(
        val items: List<AiChatItem> = emptyList(),
        val isThinking: Boolean = false,
        val errorMessage: String? = null,
        /** Tăng khi AI tạo task qua tool — Activity dùng để invalidate task cache. */
        val taskCreatedSignal: Int = 0,
        /** Subtitle ở header (vd "Về email: ..."). */
        val subtitle: String = "AI Chat",
        /** Empty state cho UI (vd "Hỏi tôi bất cứ điều gì..."). */
        val emptyHint: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { bootstrap() }

    private fun bootstrap() {
        viewModelScope.launch {
            when (context) {
                is ChatContext.Email      -> bootstrapEmail(context)
                is ChatContext.News       -> bootstrapNews(context)
                is ChatContext.Standalone -> bootstrapStandalone()
            }
        }
    }

    fun send(userInput: String) {
        val text = userInput.trim()
        if (text.isEmpty() || _state.value.isThinking) return

        val userItem = AiChatItem.Message(AiMessage(AiMessage.Role.USER, text))
        val newItems = _state.value.items + userItem
        _state.value = _state.value.copy(
            items = newItems,
            isThinking = true,
            errorMessage = null,
        )
        callAi(newItems.toMessageHistory())
    }

    fun clearError() { _state.value = _state.value.copy(errorMessage = null) }

    // ─────────────────────────────────────────────────────
    // Bootstrap per kind
    // ─────────────────────────────────────────────────────

    private suspend fun bootstrapEmail(ctx: ChatContext.Email) {
        val thread  = emailRepository.getThread(ctx.emailId).getOrNull().orEmpty()
        val lastMsg = thread.lastOrNull()

        val initial  = buildEmailInitialMessage(ctx.subject, lastMsg)
        val userItem = AiChatItem.Message(AiMessage(AiMessage.Role.USER, initial))

        _state.value = _state.value.copy(
            items     = listOf(userItem),
            isThinking = true,
            subtitle  = ctx.subject?.let { "Về email: $it" } ?: "AI Chat",
        )
        callAi(listOf(userItem.message))
    }

    private suspend fun bootstrapNews(ctx: ChatContext.News) {
        val news = newsRepository.getNews(ctx.newsId)
        if (news == null) {
            _state.value = _state.value.copy(
                errorMessage = "Không tìm thấy news. Vui lòng quay lại danh sách.",
            )
            return
        }

        val initial  = buildNewsInitialMessage(news)
        val userItem = AiChatItem.Message(AiMessage(AiMessage.Role.USER, initial))

        _state.value = _state.value.copy(
            items     = listOf(userItem),
            isThinking = true,
            subtitle  = buildNewsSubtitle(news),
        )
        callAi(listOf(userItem.message))
    }

    /**
     * Standalone: không seed message, không call AI. Chờ user gõ tin đầu.
     * UI hiện empty hint.
     */
    private fun bootstrapStandalone() {
        _state.value = _state.value.copy(
            items     = emptyList(),
            isThinking = false,
            subtitle  = "Hỏi tôi bất cứ điều gì",
            emptyHint = "Bắt đầu bằng một câu hỏi...\nVD: \"Tóm tắt lịch học tuần này\"",
        )
    }

    // ─────────────────────────────────────────────────────
    // AI call (route theo context)
    // ─────────────────────────────────────────────────────

    private fun callAi(history: List<AiMessage>) {
        viewModelScope.launch {
            val result = when (context) {
                is ChatContext.Email -> aiRepository.emailChat(context.emailId, history)

                is ChatContext.News -> {
                    val n = newsRepository.getNews(context.newsId)
                    aiRepository.chat(
                        history = history,
                        systemInstruction = n?.let { buildNewsSystemInstruction(it) },
                    )
                }

                is ChatContext.Standalone -> aiRepository.chat(
                    history = history,
                    systemInstruction = STANDALONE_SYSTEM_INSTRUCTION,
                )
            }

            result.fold(
                onSuccess = { res ->
                    val toolItems = res.toolCalls
                    val replyItem = AiChatItem.Message(
                        AiMessage(
                            AiMessage.Role.ASSISTANT,
                            res.reply.ifBlank { "(không có nội dung)" },
                        ),
                    )
                    val taskCreated = toolItems.any { it.name == "create_task" && it.success }
                    _state.value = _state.value.copy(
                        items = _state.value.items + toolItems + replyItem,
                        isThinking = false,
                        taskCreatedSignal =
                            if (taskCreated) _state.value.taskCreatedSignal + 1
                            else _state.value.taskCreatedSignal,
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isThinking = false,
                        errorMessage = err.message ?: "AI lỗi",
                    )
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────
    // Builders
    // ─────────────────────────────────────────────────────

    private fun buildEmailInitialMessage(subject: String?, msg: ThreadMessage?): String =
        buildString {
            append("Tôi đang đọc email")
            subject?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            append(".")
            msg?.sender?.let { append("\nTừ: ").append(it) }
            msg?.receivedAt?.let { append("\nNgày: ").append(it) }
            if (msg == null) append("\n(Không tìm thấy nội dung email trong cache local.)")
        }

    private fun buildNewsInitialMessage(n: News): String {
        val kindLabel = when (n.kind) {
            NewsKind.NEWS -> "tin tức"
            NewsKind.PLAN -> "kế hoạch"
        }
        val metaParts = buildList {
            n.tag?.takeIf { it.isNotBlank() }?.let { add("Loại: $it") }
            if (n.publishedAt > 0L) add("Ngày đăng: ${DATE_FMT.format(Date(n.publishedAt))}")
            n.sourceName?.takeIf { it.isNotBlank() }?.let { add("Nguồn: $it") }
        }
        val meta = if (metaParts.isEmpty()) "" else metaParts.joinToString(" • ") + "\n\n"
        val body = n.summary?.takeIf { it.isNotBlank() } ?: "(Không có nội dung chi tiết)"

        return buildString {
            append("Đây là $kindLabel mới từ HUST CTT:\n\n")
            append("📌 ").append(n.title).append("\n")
            if (meta.isNotEmpty()) append(meta)
            append(body)
        }
    }

    private fun buildNewsSubtitle(n: News): String {
        val prefix = when (n.kind) {
            NewsKind.NEWS -> "Về tin tức"
            NewsKind.PLAN -> "Về kế hoạch"
        }
        return "$prefix: ${n.title}"
    }

    private fun buildNewsSystemInstruction(n: News): String {
        val typeLabel = when (n.kind) {
            NewsKind.NEWS -> "tin tức / thông báo"
            NewsKind.PLAN -> "kế hoạch học tập / lịch thi"
        }
        return """
            Bạn là trợ lý cho sinh viên Đại học Bách Khoa Hà Nội.
            Người dùng đang đọc 1 $typeLabel từ Cổng thông tin sinh viên (HUST CTT).
            Tin nhắn đầu tiên của họ chính là nội dung bài viết — không phải câu hỏi.
            Hãy xác nhận đã đọc và gợi ý họ có thể hỏi gì
            (vd: tóm tắt, deadline, ai liên quan, có cần tạo task nhắc lịch không).
            Nếu user yêu cầu tạo task / nhắc nhở liên quan đến mốc thời gian trong bài,
            dùng tool create_task với end_time hợp lý.
            Luôn trả lời bằng tiếng Việt.
        """.trimIndent()
    }

    private fun List<AiChatItem>.toMessageHistory(): List<AiMessage> =
        filterIsInstance<AiChatItem.Message>().map { it.message }

    companion object {
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))

        private val STANDALONE_SYSTEM_INSTRUCTION = """
            Bạn là trợ lý AI cho sinh viên Đại học Bách Khoa Hà Nội.
            Trả lời câu hỏi của người dùng ngắn gọn, đúng trọng tâm.
            Nếu họ yêu cầu tạo task / nhắc lịch học / lịch thi, dùng tool create_task
            với end_time hợp lý.
            Luôn trả lời bằng tiếng Việt.
        """.trimIndent()

        fun provideFactory(
            context: ChatContext,
            aiRepository: AiRepository,
            emailRepository: EmailRepository,
            newsRepository: NewsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiChatViewModel(context, aiRepository, emailRepository, newsRepository) as T
        }
    }
}
