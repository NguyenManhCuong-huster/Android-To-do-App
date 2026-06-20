package com.project.hustassistant.ui.aichat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.hustassistant.data.ai.AiChatItem
import com.project.hustassistant.data.ai.AiMessage
import com.project.hustassistant.data.ai.AiRepository
import com.project.hustassistant.data.ai.AiStreamEvent
import com.project.hustassistant.data.ai.AttachmentRef
import com.project.hustassistant.data.ai.ChatHistoryRepository
import com.project.hustassistant.data.attachment.Attachment
import com.project.hustassistant.data.attachment.AttachmentRepository
import com.project.hustassistant.data.email.EmailRepository
import com.project.hustassistant.data.email.ThreadMessage
import com.project.hustassistant.data.grade.GradeRepository
import com.project.hustassistant.data.news.News
import com.project.hustassistant.data.news.NewsKind
import com.project.hustassistant.data.news.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class AiChatViewModel(
    private val context: ChatContext,
    private val aiRepository: AiRepository,
    private val emailRepository: EmailRepository,
    private val newsRepository: NewsRepository,
    private val attachmentRepository: AttachmentRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val gradeRepository: GradeRepository,
    private val resumeSessionId: String? = null,
) : ViewModel() {

    data class UiState(
        val items: List<AiChatItem> = emptyList(),
        val isThinking: Boolean = false,
        val errorMessage: String? = null,
        val taskCreatedSignal: Int = 0,
        val subtitle: String = "AI Chat",
        val emptyHint: String = "",

        /** File user đã upload nhưng chưa gửi (chỉ standalone & các turn sau). */
        val pendingAttachments: List<AttachmentRef> = emptyList(),
        /** Số file đang upload (để hiện progress + disable send). */
        val uploadingCount: Int = 0,

        /**
         * Nội dung cần ĐỔ vào ô soạn (one-shot) — dùng khi user bấm Sửa 1 tin.
         * Activity quan sát, set vào EditText rồi gọi [consumeInputDraft].
         */
        val inputDraft: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Phiên chat hiện tại (để lưu/tiếp tục). */
    private var sessionId: String? = null

    /** Số thứ tự tin nhắn kế tiếp trong phiên (giữ thứ tự khi lưu). */
    private var seq = 0L

    /** Khoá tạo phiên lazy để tránh tạo trùng khi 2 persist chạy song song. */
    private val sessionMutex = Mutex()

    /**
     * Khi user "Sửa" 1 tin → bắt đầu nhánh chat mới: các tin TRƯỚC tin được sửa
     * được giữ tạm ở đây và chỉ ghi vào phiên mới khi user thực sự bấm gửi (tránh
     * tạo phiên rác nếu user đổi ý). null = không ở trạng thái nhánh.
     */
    private var branchPrefix: List<AiMessage>? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            // CHỈ resume khi mở từ màn Lịch sử (có sessionId cụ thể).
            val forced = resumeSessionId
            if (forced != null) {
                val stored = chatHistoryRepository.loadMessages(forced)
                sessionId = forced
                seq = stored.size.toLong()
                _state.value = _state.value.copy(
                    items = stored.map { AiChatItem.Message(it) },
                    isThinking = false,
                    subtitle = resumeSubtitle(context),
                    emptyHint = "",
                )
                return@launch
            }

            // Mở MỚI: standalone trống, email/news seed như cũ.
            // Phiên được tạo LAZY ở lần lưu tin đầu (ensureSession) → không tạo phiên rỗng.
            when (context) {
                is ChatContext.Email -> bootstrapEmail(context)
                is ChatContext.News -> bootstrapNews(context)
                is ChatContext.Standalone -> bootstrapStandalone()
            }
        }
    }

    // ── persistence helpers (MỚI 2026-06) ───────────────────
    private fun ChatContext.persistType(): String = when (this) {
        is ChatContext.Email -> ChatHistoryRepository.TYPE_EMAIL
        is ChatContext.News -> ChatHistoryRepository.TYPE_NEWS
        ChatContext.Standalone -> ChatHistoryRepository.TYPE_STANDALONE
    }

    private fun ChatContext.persistKey(): String = when (this) {
        is ChatContext.Email -> emailId
        is ChatContext.News -> newsId
        ChatContext.Standalone -> ChatHistoryRepository.KEY_STANDALONE
    }

    private suspend fun resumeSubtitle(ctx: ChatContext): String = when (ctx) {
        is ChatContext.Email -> ctx.subject?.let { "Về email: $it" } ?: "AI Chat"
        is ChatContext.News -> runCatching { newsRepository.getNews(ctx.newsId) }
            .getOrNull()?.let { buildNewsSubtitle(it) } ?: "AI Chat"

        ChatContext.Standalone -> "Hỏi tôi bất cứ điều gì"
    }

    /** Lấy phiên hiện tại, tạo lazy nếu chưa có (lần lưu tin đầu). */
    private suspend fun ensureSession(): String = sessionMutex.withLock {
        sessionId ?: chatHistoryRepository
            .createSession(context.persistType(), context.persistKey())
            .also { sessionId = it }
    }

    /** Lưu 1 tin nhắn vào phiên hiện tại (async, không chặn UI). */
    private fun persist(message: AiMessage) {
        val s = seq++
        viewModelScope.launch {
            runCatching {
                val sid = ensureSession()
                chatHistoryRepository.appendMessage(sid, message, s)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────

    fun send(userInput: String) {
        val text = userInput.trim()
        val current = _state.value
        if (current.isThinking || current.uploadingCount > 0) return
        if (text.isEmpty() && current.pendingAttachments.isEmpty()) return

        // Bao gồm pendingAttachments vào message user vừa gửi → rồi clear pending.
        val msg = AiMessage(
            role = AiMessage.Role.USER,
            content = text.ifEmpty { "(đính kèm file)" },
            attachments = current.pendingAttachments,
        )
        val userItem = AiChatItem.Message(msg)
        val newItems = current.items + userItem + AiChatItem.Thinking
        _state.value = current.copy(
            items = newItems,
            isThinking = true,
            errorMessage = null,
            pendingAttachments = emptyList(),
            inputDraft = null,
        )
        // Nếu đang ở nhánh "Sửa": ghi prefix vào phiên mới trước, rồi tới tin này.
        branchPrefix?.let { prefix ->
            branchPrefix = null
            prefix.forEach { persist(it) }
        }
        persist(msg)
        callAi(newItems.toMessageHistory())
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /**
     * "Sửa" 1 tin user: tách thành nhánh chat MỚI gồm các tin TRƯỚC tin đó, đẩy
     * nội dung tin đó (kèm file) trở lại ô soạn để user chỉnh rồi gửi lại. Phiên
     * cũ giữ nguyên trong Lịch sử; phiên mới tạo lazy khi user bấm gửi.
     */
    fun editFrom(item: AiChatItem.Message) {
        val current = _state.value
        if (current.isThinking || item.role != AiMessage.Role.USER) return
        val idx = current.items.indexOfFirst { it === item }
        if (idx < 0) return

        val prefix = current.items.subList(0, idx)
            .filterNot { it is AiChatItem.Thinking || it is AiChatItem.Streaming }
        val prefixMessages = prefix.filterIsInstance<AiChatItem.Message>().map { it.message }

        sessionId = null
        seq = 0
        branchPrefix = prefixMessages

        _state.value = current.copy(
            items = prefix,
            isThinking = false,
            errorMessage = null,
            pendingAttachments = item.attachments,
            inputDraft = item.content,
        )
    }

    /** Activity gọi sau khi đã đổ [UiState.inputDraft] vào ô soạn. */
    fun consumeInputDraft() {
        if (_state.value.inputDraft != null) {
            _state.value = _state.value.copy(inputDraft = null)
        }
    }

    /**
     * Upload nhiều file lên server (parallel). Khi xong → append vào pendingAttachments.
     * UI dùng state.uploadingCount > 0 để disable send.
     */
    fun attachFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.value = _state.value.copy(
            uploadingCount = _state.value.uploadingCount + uris.size,
        )
        for (uri in uris) {
            viewModelScope.launch {
                val res = aiRepository.uploadAttachment(uri)
                _state.value = _state.value.copy(
                    uploadingCount = (_state.value.uploadingCount - 1).coerceAtLeast(0),
                )
                res.fold(
                    onSuccess = { ref ->
                        _state.value = _state.value.copy(
                            pendingAttachments = _state.value.pendingAttachments + ref,
                        )
                    },
                    onFailure = { err ->
                        Log.w(TAG, "upload failed: ${err.message}", err)
                        _state.value = _state.value.copy(
                            errorMessage = "Upload thất bại: ${err.message ?: "lỗi không xác định"}",
                        )
                    },
                )
            }
        }
    }

    /** Bỏ 1 pending attachment (user đổi ý trước khi gửi). */
    fun removePending(attachmentId: String) {
        _state.value = _state.value.copy(
            pendingAttachments = _state.value.pendingAttachments.filterNot { it.id == attachmentId },
        )
    }

    // ─────────────────────────────────────────────────────
    // Bootstrap per kind
    // ─────────────────────────────────────────────────────

    private suspend fun bootstrapEmail(ctx: ChatContext.Email) {
        val thread = emailRepository.getThread(ctx.emailId).getOrNull().orEmpty()
        val lastMsg = thread.lastOrNull()

        // Lấy attachments của TOÀN BỘ messages trong thread, dedupe theo id.
        val sourceAtts: List<Attachment> = runCatching {
            thread.flatMap { msg ->
                attachmentRepository.getForEmail(msg.localEmailId)
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())

        // SỬA bug "AI không đọc được file": trước đây filter `!isInline`. Nhưng
        // gmailService.partDisposition() cũ coi Content-ID là dấu hiệu inline →
        // Outlook gắn Content-ID cho mọi part khiến file PDF/HTML/DOCX bị đánh
        // nhầm inline → bubble user không hiện chip + seed message không nhắc
        // tới file → AI tin là không có file. Logic mới: filter theo
        // isTextExtractable() — match server-side aiService.buildAttachmentsSystemNote().
        val seedRefs = sourceAtts.filter { it.isTextExtractable() }.map { it.toRef() }
        val initial = buildEmailInitialMessage(ctx.subject, lastMsg, sourceAtts)
        val userItem = AiChatItem.Message(
            AiMessage(
                role = AiMessage.Role.USER,
                content = initial,
                attachments = seedRefs,                                        // ← embed vào seed msg
            )
        )

        _state.value = _state.value.copy(
            items = listOf(userItem, AiChatItem.Thinking),
            isThinking = true,
            subtitle = ctx.subject?.let { "Về email: $it" } ?: "AI Chat",
        )
        persist(userItem.message)
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

        val sourceAtts = runCatching {
            attachmentRepository.getForNews(ctx.newsId)
        }.getOrDefault(emptyList())

        val seedRefs = sourceAtts.filter { it.isTextExtractable() }.map { it.toRef() }
        val initial = buildNewsInitialMessage(news, sourceAtts)
        val userItem = AiChatItem.Message(
            AiMessage(
                role = AiMessage.Role.USER,
                content = initial,
                attachments = seedRefs,
            )
        )

        _state.value = _state.value.copy(
            items = listOf(userItem, AiChatItem.Thinking),
            isThinking = true,
            subtitle = buildNewsSubtitle(news),
        )
        persist(userItem.message)
        callAi(listOf(userItem.message))
    }

    private fun bootstrapStandalone() {
        _state.value = _state.value.copy(
            items = emptyList(),
            isThinking = false,
            subtitle = "Hỏi tôi bất cứ điều gì",
            emptyHint = "Bắt đầu bằng một câu hỏi...\nVD: \"Tóm tắt lịch học tuần này\"" +
                    "\nHoặc đính kèm file để hỏi AI về nó.",
        )
    }

    // ─────────────────────────────────────────────────────
    // AI call (route theo context)
    // ─────────────────────────────────────────────────────

    /**
     * Gọi AI ở chế độ STREAMING. Bong bóng assistant cập nhật dần theo từng
     * delta; card tool-call hiện ngay khi 1 tool chạy xong; khi [AiStreamEvent.Done]
     * thì chốt nội dung đầy đủ + reference + lưu lịch sử.
     *
     * [baseItems] = snapshot các item TRƯỚC phản hồi của lượt này (gồm tin user
     * vừa gửi, đã bỏ Thinking). Mỗi lần render lại = baseItems + tool cards +
     * (bong bóng đang stream | Thinking nếu chưa có chữ nào).
     */
    private fun callAi(history: List<AiMessage>) {
        val baseItems: List<AiChatItem> = _state.value.items.filterNot { it is AiChatItem.Thinking }
        val collectedTools = mutableListOf<AiChatItem.ToolCall>()
        val buffer = StringBuilder()
        var streaming = false

        fun render() {
            val tail: List<AiChatItem> =
                if (streaming) listOf(AiChatItem.Streaming(buffer.toString()))
                else listOf(AiChatItem.Thinking)
            _state.value = _state.value.copy(items = baseItems + collectedTools + tail)
        }

        viewModelScope.launch {
            val flow = when (context) {
                is ChatContext.Email      -> aiRepository.emailChatStream(context.emailId, history)
                is ChatContext.News       -> aiRepository.newsChatStream(context.newsId, history)
                is ChatContext.Standalone -> aiRepository.chatStream(history, STANDALONE_SYSTEM_INSTRUCTION)
            }

            runCatching {
                flow.collect { ev ->
                    when (ev) {
                        is AiStreamEvent.Delta -> {
                            buffer.append(ev.text)
                            streaming = true
                            render()
                        }

                        is AiStreamEvent.Tool -> {
                            collectedTools.add(AiToolCallPresenter.present(ev.toolCall))
                            render()
                        }

                        is AiStreamEvent.Done  -> finishStream(baseItems, collectedTools, ev)

                        is AiStreamEvent.Error -> failStream(baseItems, collectedTools, ev.message)
                    }
                }
            }.onFailure { err ->
                failStream(baseItems, collectedTools, err.message ?: "AI lỗi")
            }
        }
    }

    private fun finishStream(
        baseItems: List<AiChatItem>,
        tools: List<AiChatItem.ToolCall>,
        done: AiStreamEvent.Done,
    ) {
        val replyItem = AiChatItem.Message(
            AiMessage(
                role = AiMessage.Role.ASSISTANT,
                content = done.reply.ifBlank { "(không có nội dung)" },
                references = done.references,
            ),
        )
        persist(replyItem.message)

        val taskCreated = tools.any {
            (it.name == "create_task" || it.name == "create_weekly_tasks") && it.success
        }
        val gradeCreated = tools.any {
            (it.name == "create_grade" || it.name == "create_grades") && it.success
        }
        if (gradeCreated) {
            viewModelScope.launch { runCatching { gradeRepository.sync() } }
        }

        val updatedItems: List<AiChatItem> = buildList {
            addAll(baseItems)
            addAll(tools)
            add(replyItem)
        }
        _state.value = _state.value.copy(
            items = updatedItems,
            isThinking = false,
            taskCreatedSignal =
                if (taskCreated) _state.value.taskCreatedSignal + 1
                else _state.value.taskCreatedSignal,
        )
    }

    private fun failStream(
        baseItems: List<AiChatItem>,
        tools: List<AiChatItem.ToolCall>,
        message: String,
    ) {
        // Bỏ bong bóng stream/Thinking, giữ lại các tool card đã chạy được.
        val kept: List<AiChatItem> = baseItems + tools
        _state.value = _state.value.copy(
            items = kept,
            isThinking = false,
            errorMessage = message,
        )
    }

    // ─────────────────────────────────────────────────────
    // Builders
    // ─────────────────────────────────────────────────────

    private fun buildEmailInitialMessage(
        subject: String?,
        msg: ThreadMessage?,
        attachments: List<Attachment>,
    ): String = buildString {
        append("Tôi đang đọc email")
        subject?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        append(".")
        msg?.sender?.let { append("\nTừ: ").append(it) }
        msg?.receivedAt?.let { append("\nNgày: ").append(it) }

        appendAttachmentsHint(attachments)

        if (msg == null) append("\n(Không tìm thấy nội dung email trong cache local.)")
    }

    private fun buildNewsInitialMessage(n: News, attachments: List<Attachment>): String {
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

            appendAttachmentsHint(attachments)
        }
    }

    /**
     * Chỉ thêm 1 dòng hint ngắn để báo có file — chi tiết (kích thước, mime)
     * đã được render qua attachment chips trong bubble UI + qua system
     * instruction server-side. Không lặp lại để tránh tốn token.
     *
     * SỬA (bug fix): match logic server — chỉ đếm file isTextExtractable().
     */
    private fun StringBuilder.appendAttachmentsHint(attachments: List<Attachment>) {
        val visible = attachments.filter { it.isTextExtractable() }
        if (visible.isEmpty()) return
        append("\n\n📎 Có ").append(visible.size).append(" file đính kèm (xem chip phía dưới).")
    }

    private fun buildNewsSubtitle(n: News): String {
        val prefix = when (n.kind) {
            NewsKind.NEWS -> "Về tin tức"
            NewsKind.PLAN -> "Về kế hoạch"
        }
        return "$prefix: ${n.title}"
    }

    private fun List<AiChatItem>.toMessageHistory(): List<AiMessage> =
        filterIsInstance<AiChatItem.Message>().map { it.message }

    private fun Attachment.toRef() = AttachmentRef(
        id = id,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
    )

    /**
     * Kiểm tra file có thể trích text qua AI tool `read_attachment` hay không.
     * Phải giữ KHỚP với:
     *   - Server: aiService.buildAttachmentsSystemNote() + ai.js buildFileListNote()
     *   - Server ENV: ATTACHMENT_TEXT_EXTRACT_EXTS (mặc định ghi rõ dưới đây)
     *
     * Nếu server đổi danh sách extension qua ENV, sửa list bên dưới cho khớp.
     */
    private fun Attachment.isTextExtractable(): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty() && ext in TEXT_EXTRACT_EXTS) return true
        val m = (mimeType ?: "").lowercase(Locale.ROOT)
        if (m.isEmpty()) return false
        if (m.startsWith("text/")) return true
        return TEXT_EXTRACT_MIME_KEYWORDS.any { it in m }
    }

    companion object {
        private const val TAG = "AiChatViewModel"
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi"))

        /**
         * Phải khớp với ENV server `ATTACHMENT_TEXT_EXTRACT_EXTS`.
         * Server mặc định: pdf,docx,xlsx,xls,csv,html,htm,txt,md,json,xml,log
         */
        private val TEXT_EXTRACT_EXTS = setOf(
            "pdf", "docx", "xlsx", "xls", "csv",
            "html", "htm", "txt", "md", "json", "xml", "log",
        )

        /** Keyword phụ trợ trong MIME (khi filename không có extension). */
        private val TEXT_EXTRACT_MIME_KEYWORDS = listOf(
            "pdf", "wordprocessingml", "spreadsheetml", "ms-excel", "csv", "html",
        )

        private val STANDALONE_SYSTEM_INSTRUCTION = """
            Bạn là trợ lý AI cho sinh viên Đại học Bách Khoa Hà Nội.
            Trả lời câu hỏi của người dùng ngắn gọn, đúng trọng tâm.
            Nếu user đính kèm file (xem mục FILE ĐÍNH KÈM nếu có), bạn có thể
            dùng tool read_attachment để đọc nội dung khi user yêu cầu.
            Nếu họ yêu cầu tạo task / nhắc lịch học / lịch thi, dùng tool create_task
            với end_time hợp lý.
            Nếu họ muốn lưu điểm / kết quả học tập: 1 môn → dùng tool create_grade,
            nhiều môn (bảng điểm, paste danh sách) → dùng tool create_grades (1 lần).
            Luôn trả lời bằng tiếng Việt.
        """.trimIndent()

        fun provideFactory(
            context: ChatContext,
            aiRepository: AiRepository,
            emailRepository: EmailRepository,
            newsRepository: NewsRepository,
            attachmentRepository: AttachmentRepository,
            chatHistoryRepository: ChatHistoryRepository,
            gradeRepository: GradeRepository,
            resumeSessionId: String? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiChatViewModel(
                    context,
                    aiRepository,
                    emailRepository,
                    newsRepository,
                    attachmentRepository,
                    chatHistoryRepository,
                    gradeRepository,
                    resumeSessionId,
                ) as T
        }
    }
}