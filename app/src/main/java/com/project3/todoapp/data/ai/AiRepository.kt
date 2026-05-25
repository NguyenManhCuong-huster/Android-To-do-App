package com.project3.todoapp.data.ai

import com.project3.todoapp.data.ai.network.AiApi
import com.project3.todoapp.data.ai.network.AiChatBody
import com.project3.todoapp.data.ai.network.AiEmailChatBody
import com.project3.todoapp.data.ai.network.AiMessageBody
import com.project3.todoapp.data.ai.network.AiToolCallDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


/** Một message trong chat — model dùng cho UI / ViewModel. */
data class AiMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT }
}

class AiRepository(
    private val aiApi: AiApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Chat có context email: server tự fetch thread, build system instruction.
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

    /** Chat thuần — không có email context. */
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

    private fun AiMessage.toBody() = AiMessageBody(
        role = if (role == AiMessage.Role.USER) "user" else "assistant",
        content = content,
    )

    // ─────────────────────────────────────────────────────────────
    // DTO → UI mapping
    // ─────────────────────────────────────────────────────────────
    private fun AiToolCallDto.toUiModel(): AiChatItem.ToolCall {
        val resultMap = result.orEmpty()
        val success = resultMap["success"] as? Boolean ?: false

        return when (name) {
            "create_task" -> mapCreateTask(success, resultMap)
            "create_weekly_tasks" -> mapCreateWeeklyTasks(success, resultMap)
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

    /**
     * Map kết quả tool `create_weekly_tasks` (loop tuần tổng quát).
     *
     * Shape result (success), từ server v5:
     *   {
     *     success: true,
     *     summary: {
     *       title, task_type, created, skipped, day_of_week,    // ISO 1..7
     *       loop_start_date, loop_end_date,
     *       first_start_time, last_start_time,                  // có thể null
     *       first_end_time,   last_end_time,                    // fallback nếu start null
     *       tags: [...]
     *     },
     *     task_ids: [...]
     *   }
     */
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

        // Date range: ưu tiên start_time, fall back end_time (khi task không có giờ cụ thể).
        val firstStartIso = summary["first_start_time"] as? String
        val lastStartIso = summary["last_start_time"] as? String
        val firstEndIso = summary["first_end_time"] as? String
        val lastEndIso = summary["last_end_time"] as? String
        val firstAnchorIso = firstStartIso ?: firstEndIso
        val lastAnchorIso = lastStartIso ?: lastEndIso

        val tagsRaw = summary["tags"] as? List<Map<String, Any?>> ?: emptyList()

        // Đơn vị + label theo task_type
        val (typeLabel, unit) = when (taskType) {
            "CLASS" -> "Lịch học" to "buổi"
            "EXAM" -> "Lịch thi" to "buổi"
            else -> "Việc lặp tuần" to "lần"
        }

        val dowLabel = formatDayOfWeek(dow)

        // Time label: chỉ hiện khi có start_time thực sự (không phải fallback từ end_time),
        // và không phải "00:00" (default khi không có giờ).
        val timeLabel = firstStartIso
            ?.let { extractHm(it) }
            ?.takeIf { it != "00:00" }

        val rangeLabel = if (firstAnchorIso != null && lastAnchorIso != null) {
            val a = formatDateShort(firstAnchorIso)
            val b = formatDateShort(lastAnchorIso)
            if (a != null && b != null && a != b) "$a → $b"
            else a   // chỉ 1 buổi
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

    // ─────────────────────────────────────────────────────────────
    // Date / time format helpers
    // ─────────────────────────────────────────────────────────────

    /** "2025-02-19T14:10:00+07:00" → "19/02 14:10" (dùng cho create_task). */
    private fun formatDeadline(iso: String): String? {
        return try {
            val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            // Postgres TIMESTAMPTZ thường có ms → strip kẻo parse fail
            val cleaned = iso.replace(Regex("\\.\\d+"), "")
            val date = src.parse(cleaned) ?: return null
            val out = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            out.format(date)
        } catch (_: Throwable) {
            null
        }
    }

    /** "2025-02-19T14:10:00+07:00" → "19/02"; null nếu parse fail. */
    private fun formatDateShort(iso: String): String? = try {
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val cleaned = iso.replace(Regex("\\.\\d+"), "")
        val date = src.parse(cleaned) ?: return null
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    } catch (_: Throwable) {
        null
    }

    /** "2025-02-19T14:10:00+07:00" → "14:10". Lấy nguyên text từ ISO (giữ giờ VN). */
    private fun extractHm(iso: String): String? =
        Regex("T(\\d{2}:\\d{2})").find(iso)?.groupValues?.get(1)

    /**
     * ISO 8601 weekday: 1=Thứ 2 (Mon), 2=Thứ 3, ..., 7=Chủ Nhật (Sun).
     *
     * CHÚ Ý: convention này khác CTT HUST cũ (2=T2 ... 7=T7, 8=CN).
     * Đã sync với server tool `create_weekly_tasks` v5.
     */
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
