package com.project3.todoapp.data.ai

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.project3.todoapp.data.ai.network.AiApi
import com.project3.todoapp.data.ai.network.AiChatBody
import com.project3.todoapp.data.ai.network.AiEmailChatBody
import com.project3.todoapp.data.ai.network.AiMessageBody
import com.project3.todoapp.data.ai.network.AiNewsChatBody
import com.project3.todoapp.data.ai.network.AiToolCallDto
import com.project3.todoapp.data.ai.network.AttachmentRefBody
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


data class AiMessage(
    val role: Role,
    val content: String,
    val attachments: List<AttachmentRef> = emptyList(),
) {
    enum class Role { USER, ASSISTANT }
}

class AiRepository(
    private val aiApi: AiApi,
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ═══════════════════════════════════════════════════════════
    //  Chat endpoints
    // ═══════════════════════════════════════════════════════════

    /**
     * Chat có context email: server tự fetch thread + attachments của thread.
     * Per-message attachments (nếu có) vẫn được gửi cho server validate +
     * merge vào "allowed IDs".
     */
    suspend fun emailChat(
        emailId: String,
        history: List<AiMessage>,
    ): Result<AiChatResult> = runCatching {
        withContext(dispatcher) {
            val res = aiApi.emailChat(
                AiEmailChatBody(
                    email_id = emailId,
                    messages = history.map { it.toBody() },
                )
            )
            require(res.success) { res.message ?: "AI request failed" }
            val data = res.data
            AiChatResult(
                reply = data?.reply.orEmpty(),
                toolCalls = data?.tool_calls.orEmpty().map { it.toUiModel() },
            )
        }
    }

    /**
     * Chat có context news: server tự fetch news + attachments của news.
     * MỚI 2026-05-31.
     */
    suspend fun newsChat(
        newsId: String,
        history: List<AiMessage>,
    ): Result<AiChatResult> = runCatching {
        withContext(dispatcher) {
            val res = aiApi.newsChat(
                AiNewsChatBody(
                    news_id = newsId,
                    messages = history.map { it.toBody() },
                )
            )
            require(res.success) { res.message ?: "AI request failed" }
            val data = res.data
            AiChatResult(
                reply = data?.reply.orEmpty(),
                toolCalls = data?.tool_calls.orEmpty().map { it.toUiModel() },
            )
        }
    }

    /** Chat thuần — không có email/news context. */
    suspend fun chat(
        history: List<AiMessage>,
        systemInstruction: String? = null,
    ): Result<AiChatResult> = runCatching {
        withContext(dispatcher) {
            val res = aiApi.chat(
                AiChatBody(
                    messages = history.map { it.toBody() },
                    system_instruction = systemInstruction,
                )
            )
            require(res.success) { res.message ?: "AI request failed" }
            val data = res.data
            AiChatResult(
                reply = data?.reply.orEmpty(),
                toolCalls = data?.tool_calls.orEmpty().map { it.toUiModel() },
            )
        }
    }


    /**
     * Upload 1 file từ content Uri lên server. Trả về AttachmentRef để
     * ViewModel embed vào message.attachments khi user send.
     *
     * Đọc bytes vào RAM rồi gửi multipart — phù hợp file < ~25MB (cap server).
     * Với file lớn hơn, server sẽ trả 413.
     */
    suspend fun uploadAttachment(uri: Uri): Result<AttachmentRef> = runCatching {
        withContext(dispatcher) {
            // 1) Read bytes
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Không đọc được file đã chọn.")
            if (bytes.isEmpty()) error("File rỗng.")

            // 2) Detect display name + mime
            val displayName = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
            val mime = contentResolver.getType(uri)
                ?: guessMimeFromName(displayName)
                ?: "application/octet-stream"

            // 3) Build multipart
            val reqBody = bytes.toRequestBody(mime.toMediaTypeOrNull(), 0, bytes.size)
            val part = MultipartBody.Part.createFormData("file", displayName, reqBody)

            // 4) Call API
            val res = aiApi.uploadAttachment(part)
            require(res.success) { res.message ?: "Upload thất bại" }
            val data = res.data ?: error("Server trả response rỗng.")

            AttachmentRef(
                id = data.id,
                fileName = data.file_name,
                mimeType = data.mime_type,
                sizeBytes = data.size_bytes,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            if (!it.moveToFirst()) return null
            return it.getString(idx)?.trim()?.takeIf { s -> s.isNotEmpty() }
        }
    }

    private fun guessMimeFromName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private fun AiMessage.toBody() = AiMessageBody(
        role = if (role == AiMessage.Role.USER) "user" else "assistant",
        content = content,
        attachments = attachments
            .takeIf { it.isNotEmpty() }
            ?.map { AttachmentRefBody(it.id, it.fileName, it.mimeType, it.sizeBytes) },
    )

    // ─────────────────────────────────────────────────────────────
    //  DTO → UI mapping
    //
    //  MỚI 2026-06: thêm mapping cho 5 search tool — search_emails,
    //  get_email, search_news, get_news, web_search. Mỗi tool có
    //  card hiển thị riêng để user thấy được AI vừa tra cứu gì.
    // ─────────────────────────────────────────────────────────────
    private fun AiToolCallDto.toUiModel(): AiChatItem.ToolCall {
        val resultMap = result.orEmpty()
        val success = resultMap["success"] as? Boolean ?: false

        return when (name) {
            "create_task" -> mapCreateTask(success, resultMap)
            "create_weekly_tasks" -> mapCreateWeeklyTasks(success, resultMap)
            "read_attachment" -> mapReadAttachment(success, resultMap)
            // ── MỚI 2026-06 ─────────────────────────────────────
            "search_emails" -> mapSearchEmails(success, resultMap)
            "get_email" -> mapGetEmail(success, resultMap)
            "search_news" -> mapSearchNews(success, resultMap)
            "get_news" -> mapGetNews(success, resultMap)
            "web_search" -> mapWebSearch(success, resultMap, args)
            // ────────────────────────────────────────────────────
            else -> AiChatItem.ToolCall(
                name = name,
                success = success,
                title = if (success) "Đã chạy: $name" else "Lỗi khi chạy: $name",
                errorMessage = resultMap["error"] as? String,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapCreateTask(success: Boolean, resultMap: Map<String, Any?>): AiChatItem.ToolCall {
        if (!success) {
            return AiChatItem.ToolCall(
                name = "create_task",
                success = false,
                title = "Không tạo được task",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }
        val task = resultMap["task"] as? Map<String, Any?> ?: emptyMap()
        val title = (task["title"] as? String).orEmpty()
        val taskType = (task["task_type"] as? String) ?: "TODO"
        val endTimeIso = task["end_time"] as? String
        val tagsRaw = task["tags"] as? List<Map<String, Any?>> ?: emptyList()

        val typeLabel = when (taskType) {
            "CLASS" -> "Lịch học"
            "EXAM" -> "Lịch thi"
            else -> "Việc cần làm"
        }
        val deadlineLabel = endTimeIso?.let { formatDeadline(it) }
        val subtitle = listOfNotNull(typeLabel, deadlineLabel?.let { "hạn $it" })
            .joinToString(" • ")
            .ifBlank { null }

        return AiChatItem.ToolCall(
            name = "create_task",
            success = true,
            title = "Đã tạo task: $title",
            subtitle = subtitle,
            tags = tagsRaw.map {
                AiChatItem.ToolCall.TagChip(
                    name = (it["name"] as? String).orEmpty(),
                    colorHex = it["color_hex"] as? String,
                )
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapCreateWeeklyTasks(
        success: Boolean,
        resultMap: Map<String, Any?>
    ): AiChatItem.ToolCall {
        if (!success) {
            return AiChatItem.ToolCall(
                name = "create_weekly_tasks",
                success = false,
                title = "Không tạo được task lặp tuần",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }

        val summary = resultMap["summary"] as? Map<String, Any?> ?: emptyMap()
        val title = (summary["title"] as? String).orEmpty()
        val created = (summary["created"] as? Number)?.toInt() ?: 0
        val skipped = (summary["skipped"] as? Number)?.toInt() ?: 0
        val taskType = (summary["task_type"] as? String) ?: "TODO"
        val dow = (summary["day_of_week"] as? Number)?.toInt() ?: 0

        val firstStartIso = summary["first_start_time"] as? String
        val lastStartIso = summary["last_start_time"] as? String
        val firstEndIso = summary["first_end_time"] as? String
        val lastEndIso = summary["last_end_time"] as? String
        val firstAnchorIso = firstStartIso ?: firstEndIso
        val lastAnchorIso = lastStartIso ?: lastEndIso

        val tagsRaw = summary["tags"] as? List<Map<String, Any?>> ?: emptyList()

        val (typeLabel, unit) = when (taskType) {
            "CLASS" -> "Lịch học" to "buổi"
            "EXAM" -> "Lịch thi" to "buổi"
            else -> "Việc lặp tuần" to "lần"
        }

        val dowLabel = formatDayOfWeek(dow)

        val timeLabel = firstStartIso
            ?.let { extractHm(it) }
            ?.takeIf { it != "00:00" }

        val rangeLabel = if (firstAnchorIso != null && lastAnchorIso != null) {
            val a = formatDateShort(firstAnchorIso)
            val b = formatDateShort(lastAnchorIso)
            if (a != null && b != null && a != b) "$a → $b"
            else a
        } else null

        val subtitle = listOfNotNull(
            typeLabel,
            listOfNotNull(dowLabel, timeLabel).joinToString(" ").ifBlank { null },
            rangeLabel,
            if (skipped > 0) "$skipped $unit bị bỏ qua" else null,
        ).joinToString(" • ").ifBlank { null }

        return AiChatItem.ToolCall(
            name = "create_weekly_tasks",
            success = true,
            title = if (created == 1) "Đã tạo 1 $unit: $title"
            else "Đã tạo $created $unit: $title",
            subtitle = subtitle,
            tags = tagsRaw.map {
                AiChatItem.ToolCall.TagChip(
                    name = (it["name"] as? String).orEmpty(),
                    colorHex = it["color_hex"] as? String,
                )
            },
        )
    }

    /**
     * Map kết quả tool read_attachment để hiển thị card "AI đã đọc file X".
     * Khác create_task: không trả task data, chỉ trả file_name + (optional) truncated.
     */
    private fun mapReadAttachment(
        success: Boolean,
        resultMap: Map<String, Any?>
    ): AiChatItem.ToolCall {
        if (!success) {
            val err = resultMap["error"] as? String ?: "Không đọc được file"
            // Cung cấp thêm hint nếu server liệt kê available_files
            val avail = (resultMap["available_files"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.take(3)
                ?.joinToString(", ")
            val subtitle = avail?.let { "Có sẵn: $it" }
            return AiChatItem.ToolCall(
                name = "read_attachment",
                success = false,
                title = "Không đọc được file",
                subtitle = subtitle,
                errorMessage = err,
            )
        }
        val fileName = resultMap["file_name"] as? String ?: "(file)"
        val truncated = resultMap["truncated"] as? Boolean ?: false
        val fromCache = resultMap["from_cache"] as? Boolean ?: false
        val parts = buildList {
            if (truncated) add("nội dung quá dài đã bị cắt bớt")
            if (fromCache) add("đã cache")
        }
        return AiChatItem.ToolCall(
            name = "read_attachment",
            success = true,
            title = "Đã đọc file: $fileName",
            subtitle = parts.joinToString(" • ").ifBlank { null },
        )
    }

    // ─────────────────────────────────────────────────────────────
    // MỚI 2026-06 — Mapping cho 5 search tool.
    //
    // Pattern chung:
    //   - success=false → "Không tìm được X" + errorMessage.
    //   - success=true  → title kèm count / object name; subtitle ngắn cho
    //                      query / metadata.
    // ─────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun mapSearchEmails(
        success: Boolean,
        resultMap: Map<String, Any?>,
    ): AiChatItem.ToolCall {
        if (!success) {
            return AiChatItem.ToolCall(
                name = "search_emails",
                success = false,
                title = "Tìm email thất bại",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }
        val count = (resultMap["count"] as? Number)?.toInt() ?: 0
        val query = (resultMap["query"] as? String)?.takeIf { it.isNotBlank() }

        val title = when {
            count == 0 -> "Không tìm thấy email phù hợp"
            else -> "Đã tìm thấy $count email"
        }
        val subtitle = query?.let { "Từ khoá: \"$it\"" }
            ?: if (count > 0) "Email mới nhất" else null

        return AiChatItem.ToolCall(
            name = "search_emails",
            success = true,
            title = title,
            subtitle = subtitle,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapGetEmail(
        success: Boolean,
        resultMap: Map<String, Any?>,
    ): AiChatItem.ToolCall {
        if (!success) {
            return AiChatItem.ToolCall(
                name = "get_email",
                success = false,
                title = "Không đọc được email",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }
        // Server có thể trả 1 trong 2 shape: { email: {...} } (single) hoặc
        // { messages: [...], message_count, thread_id } (include_thread).
        val email = resultMap["email"] as? Map<String, Any?>
        val messages = resultMap["messages"] as? List<*>

        return if (email != null) {
            val subject = (email["subject"] as? String)?.takeIf { it.isNotBlank() }
                ?: "(không tiêu đề)"
            val from = (email["from"] as? String)?.takeIf { it.isNotBlank() }
            val truncated = email["truncated"] as? Boolean ?: false
            AiChatItem.ToolCall(
                name = "get_email",
                success = true,
                title = "Đã đọc email: $subject",
                subtitle = listOfNotNull(
                    from?.let { "Từ: $it" },
                    if (truncated) "nội dung bị cắt" else null,
                ).joinToString(" • ").ifBlank { null },
            )
        } else {
            val count = (resultMap["message_count"] as? Number)?.toInt()
                ?: messages?.size
                ?: 0
            AiChatItem.ToolCall(
                name = "get_email",
                success = true,
                title = "Đã đọc thread email ($count tin)",
                subtitle = null,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapSearchNews(
        success: Boolean,
        resultMap: Map<String, Any?>,
    ): AiChatItem.ToolCall {
        if (!success) {
            return AiChatItem.ToolCall(
                name = "search_news",
                success = false,
                title = "Tìm tin tức thất bại",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }
        val count = (resultMap["count"] as? Number)?.toInt() ?: 0
        val query = (resultMap["query"] as? String)?.takeIf { it.isNotBlank() }
        val kind = (resultMap["kind"] as? String)?.takeIf { it.isNotBlank() }

        val kindLabel = when (kind) {
            "NEWS" -> "tin tức"
            "PLAN" -> "kế hoạch"
            else -> "tin / kế hoạch"
        }

        val title = when {
            count == 0 -> "Không tìm thấy $kindLabel phù hợp"
            else -> "Đã tìm thấy $count $kindLabel"
        }
        val subtitle = query?.let { "Từ khoá: \"$it\"" }
            ?: if (count > 0) "Mới nhất từ HUST CTT" else null

        return AiChatItem.ToolCall(
            name = "search_news",
            success = true,
            title = title,
            subtitle = subtitle,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapGetNews(
        success: Boolean,
        resultMap: Map<String, Any?>,
    ): AiChatItem.ToolCall {
        if (!success) {
            return AiChatItem.ToolCall(
                name = "get_news",
                success = false,
                title = "Không đọc được tin",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }
        val news = resultMap["news"] as? Map<String, Any?> ?: emptyMap()
        val title = (news["title"] as? String)?.takeIf { it.isNotBlank() } ?: "(không tiêu đề)"
        val kind = (news["kind"] as? String) ?: ""
        val tag = (news["tag"] as? String)?.takeIf { it.isNotBlank() }
        val truncated = news["truncated"] as? Boolean ?: false

        val kindLabel = when (kind.uppercase(Locale.ROOT)) {
            "PLAN" -> "kế hoạch"
            else -> "tin tức"
        }
        val subtitle = listOfNotNull(
            kindLabel,
            tag,
            if (truncated) "nội dung bị cắt" else null,
        ).joinToString(" • ").ifBlank { null }

        return AiChatItem.ToolCall(
            name = "get_news",
            success = true,
            title = "Đã đọc $kindLabel: $title",
            subtitle = subtitle,
        )
    }

    private fun mapWebSearch(
        success: Boolean,
        resultMap: Map<String, Any?>,
        args: Map<String, Any?>?,
    ): AiChatItem.ToolCall {
        // Lấy query từ result trước (server echo), fallback args.
        val query = (resultMap["query"] as? String)
            ?: (args?.get("query") as? String)
            ?: ""

        if (!success) {
            return AiChatItem.ToolCall(
                name = "web_search",
                success = false,
                title = if (query.isNotBlank()) "Tra web: \"$query\" thất bại"
                else "Tra web thất bại",
                errorMessage = resultMap["error"] as? String ?: "Lỗi không xác định",
            )
        }
        val count = (resultMap["count"] as? Number)?.toInt() ?: 0
        val title = if (query.isNotBlank())
            "Đã tra web: \"$query\""
        else
            "Đã tra web"
        val subtitle = if (count > 0) "$count kết quả" else "Không có kết quả"

        return AiChatItem.ToolCall(
            name = "web_search",
            success = true,
            title = title,
            subtitle = subtitle,
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Date / time format helpers (giữ nguyên)
    // ─────────────────────────────────────────────────────────────

    private fun formatDeadline(iso: String): String? {
        return try {
            val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleaned = iso.replace(Regex("\\.\\d+"), "")
            val date = src.parse(cleaned) ?: return null
            val out = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            out.format(date)
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatDateShort(iso: String): String? = try {
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val cleaned = iso.replace(Regex("\\.\\d+"), "")
        val date = src.parse(cleaned) ?: return null
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    } catch (_: Throwable) {
        null
    }

    private fun extractHm(iso: String): String? =
        Regex("T(\\d{2}:\\d{2})").find(iso)?.groupValues?.get(1)

    private fun formatDayOfWeek(dow: Int): String? = when (dow) {
        1 -> "Thứ 2"
        2 -> "Thứ 3"
        3 -> "Thứ 4"
        4 -> "Thứ 5"
        5 -> "Thứ 6"
        6 -> "Thứ 7"
        7 -> "Chủ Nhật"
        else -> null
    }
}
