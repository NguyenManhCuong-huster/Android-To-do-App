package com.project.hustassistant.ui.aichat

import com.project.hustassistant.data.ai.AiChatItem
import com.project.hustassistant.data.ai.AiToolCall
import com.project.hustassistant.data.ai.AiToolCall.NewsKind
import com.project.hustassistant.data.ai.AiToolCall.Payload
import com.project.hustassistant.data.ai.AiToolCall.TaskKind
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * AiToolCallPresenter — biến [AiToolCall] (domain) thành [AiChatItem.ToolCall]
 * (model UI cho adapter).
 *
 * Đây là NƠI DUY NHẤT chứa chuỗi hiển thị tiếng Việt + format ngày/giờ cho card
 * tool-call. Trước refactor mớ chữ này nằm trong AiRepository (tầng data).
 *
 * (Giai đoạn 4 có thể chuyển các literal sang strings.xml + ResourceProvider để
 * i18n; hiện giữ inline để bảo toàn 100% hành vi hiện tại.)
 */
object AiToolCallPresenter {

    fun present(call: AiToolCall): AiChatItem.ToolCall = when (val p = call.payload) {
        is Payload.TaskCreated -> taskCreated(p)
        is Payload.WeeklyTasksCreated -> weekly(p)
        is Payload.AttachmentRead -> attachmentRead(p)
        is Payload.AttachmentReadFailed -> attachmentFailed(call, p)
        is Payload.EmailsFound -> emailsFound(p)
        is Payload.EmailRead -> emailRead(p)
        is Payload.EmailThreadRead -> emailThread(p)
        is Payload.NewsFound -> newsFound(p)
        is Payload.NewsRead -> newsRead(p)
        is Payload.WebSearched -> webSearch(call, p)
        is Payload.GradeCreated -> gradeCreated(p)
        is Payload.GradesCreated -> gradesCreated(p)
        is Payload.GradesViewed -> gradesViewed(p)
        is Payload.TasksFound -> tasksFound(p)
        is Payload.TaskRead -> taskRead(p)
        is Payload.TagsListed -> tagsListed(p)
        null -> fallback(call)
    }

    // ─────────────────────────── create_task ───────────────────────────
    private fun taskCreated(p: Payload.TaskCreated): AiChatItem.ToolCall {
        val typeLabel = when (p.taskType) {
            TaskKind.CLASS -> "Lịch học"
            TaskKind.EXAM -> "Lịch thi"
            TaskKind.TODO -> "Việc cần làm"
        }
        val deadlineLabel = p.endTimeIso?.let { formatDeadline(it) }
        val subtitle = listOfNotNull(typeLabel, deadlineLabel?.let { "hạn $it" })
            .joinToString(" • ")
            .ifBlank { null }
        return AiChatItem.ToolCall(
            name = "create_task",
            success = true,
            title = "Đã tạo task: ${p.title}",
            subtitle = subtitle,
            tags = p.tags.map { AiChatItem.ToolCall.TagChip(it.name, it.colorHex) },
        )
    }

    // ─────────────────────── create_weekly_tasks ───────────────────────
    private fun weekly(p: Payload.WeeklyTasksCreated): AiChatItem.ToolCall {
        val (typeLabel, unit) = when (p.taskType) {
            TaskKind.CLASS -> "Lịch học" to "buổi"
            TaskKind.EXAM -> "Lịch thi" to "buổi"
            TaskKind.TODO -> "Việc lặp tuần" to "lần"
        }
        val dowLabel = formatDayOfWeek(p.dayOfWeek)
        val timeLabel = p.firstStartIso?.let { extractHm(it) }?.takeIf { it != "00:00" }

        val firstAnchor = p.firstStartIso ?: p.firstEndIso
        val lastAnchor = p.lastStartIso ?: p.lastEndIso
        val rangeLabel = if (firstAnchor != null && lastAnchor != null) {
            val a = formatDateShort(firstAnchor)
            val b = formatDateShort(lastAnchor)
            if (a != null && b != null && a != b) "$a → $b" else a
        } else null

        val subtitle = listOfNotNull(
            typeLabel,
            listOfNotNull(dowLabel, timeLabel).joinToString(" ").ifBlank { null },
            rangeLabel,
            if (p.skipped > 0) "${p.skipped} $unit bị bỏ qua" else null,
        ).joinToString(" • ").ifBlank { null }

        return AiChatItem.ToolCall(
            name = "create_weekly_tasks",
            success = true,
            title = if (p.created == 1) "Đã tạo 1 $unit: ${p.title}"
            else "Đã tạo ${p.created} $unit: ${p.title}",
            subtitle = subtitle,
            tags = p.tags.map { AiChatItem.ToolCall.TagChip(it.name, it.colorHex) },
        )
    }

    // ─────────────────────────── read_attachment ───────────────────────
    private fun attachmentRead(p: Payload.AttachmentRead): AiChatItem.ToolCall {
        val parts = buildList {
            if (p.truncated) add("nội dung quá dài đã bị cắt bớt")
            if (p.fromCache) add("đã cache")
        }
        return AiChatItem.ToolCall(
            name = "read_attachment",
            success = true,
            title = "Đã đọc file: ${p.fileName ?: "(file)"}",
            subtitle = parts.joinToString(" • ").ifBlank { null },
        )
    }

    private fun attachmentFailed(
        call: AiToolCall,
        p: Payload.AttachmentReadFailed,
    ): AiChatItem.ToolCall {
        val subtitle = p.availableFiles.take(3)
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
            ?.let { "Có sẵn: $it" }
        return AiChatItem.ToolCall(
            name = "read_attachment",
            success = false,
            title = "Không đọc được file",
            subtitle = subtitle,
            errorMessage = call.error ?: "Không đọc được file",
        )
    }

    // ─────────────────────────── search_emails ─────────────────────────
    private fun emailsFound(p: Payload.EmailsFound): AiChatItem.ToolCall {
        val title = if (p.count == 0) "Không tìm thấy email phù hợp"
        else "Đã tìm thấy ${p.count} email"
        val subtitle = p.query?.let { "Từ khoá: \"$it\"" }
            ?: if (p.count > 0) "Email mới nhất" else null
        return AiChatItem.ToolCall("search_emails", true, title, subtitle)
    }

    // ────────────────────────────── get_email ──────────────────────────
    private fun emailRead(p: Payload.EmailRead): AiChatItem.ToolCall = AiChatItem.ToolCall(
        name = "get_email",
        success = true,
        title = "Đã đọc email: ${p.subject ?: "(không tiêu đề)"}",
        subtitle = listOfNotNull(
            p.from?.let { "Từ: $it" },
            if (p.truncated) "nội dung bị cắt" else null,
        ).joinToString(" • ").ifBlank { null },
    )

    private fun emailThread(p: Payload.EmailThreadRead): AiChatItem.ToolCall = AiChatItem.ToolCall(
        name = "get_email",
        success = true,
        title = "Đã đọc thread email (${p.messageCount} tin)",
        subtitle = null,
    )

    // ─────────────────────────────── search_news ───────────────────────
    private fun newsFound(p: Payload.NewsFound): AiChatItem.ToolCall {
        val kindLabel = when (p.kind) {
            NewsKind.NEWS -> "tin tức"
            NewsKind.PLAN -> "kế hoạch"
            NewsKind.UNKNOWN -> "tin / kế hoạch"
        }
        val title = if (p.count == 0) "Không tìm thấy $kindLabel phù hợp"
        else "Đã tìm thấy ${p.count} $kindLabel"
        val subtitle = p.query?.let { "Từ khoá: \"$it\"" }
            ?: if (p.count > 0) "Mới nhất từ HUST CTT" else null
        return AiChatItem.ToolCall("search_news", true, title, subtitle)
    }

    // ──────────────────────────────── get_news ─────────────────────────
    private fun newsRead(p: Payload.NewsRead): AiChatItem.ToolCall {
        val kindLabel = when (p.kind) {
            NewsKind.PLAN -> "kế hoạch"
            else -> "tin tức"
        }
        val subtitle = listOfNotNull(
            kindLabel,
            p.tag,
            if (p.truncated) "nội dung bị cắt" else null,
        ).joinToString(" • ").ifBlank { null }
        return AiChatItem.ToolCall(
            name = "get_news",
            success = true,
            title = "Đã đọc $kindLabel: ${p.title ?: "(không tiêu đề)"}",
            subtitle = subtitle,
        )
    }

    // ─────────────────────────────── web_search ────────────────────────
    private fun webSearch(call: AiToolCall, p: Payload.WebSearched): AiChatItem.ToolCall {
        val q = p.query
        if (!call.success) {
            return AiChatItem.ToolCall(
                name = "web_search",
                success = false,
                title = if (!q.isNullOrBlank()) "Tra web: \"$q\" thất bại" else "Tra web thất bại",
                errorMessage = call.error ?: "Lỗi không xác định",
            )
        }
        return AiChatItem.ToolCall(
            name = "web_search",
            success = true,
            title = if (!q.isNullOrBlank()) "Đã tra web: \"$q\"" else "Đã tra web",
            subtitle = if (p.count > 0) "${p.count} kết quả" else "Không có kết quả",
        )
    }

    // ─────────────────────────────── create_grade ──────────────────────
    private fun gradeCreated(p: Payload.GradeCreated): AiChatItem.ToolCall {
        val verb = if (p.action == "updated") "Đã cập nhật điểm" else "Đã ghi điểm"
        val course = listOf(p.courseCode, p.courseName)
            .filter { it.isNotBlank() }
            .joinToString(" – ")
            .ifBlank { "(học phần)" }
        val subtitle = listOfNotNull(
            semesterLabel(p.semester),
            p.letter?.let { "điểm $it" } ?: "chưa có điểm",
        ).joinToString(" • ").ifBlank { null }
        return AiChatItem.ToolCall(
            name = "create_grade",
            success = true,
            title = "$verb: $course",
            subtitle = subtitle,
        )
    }

    // ─────────────────────────────── create_grades ─────────────────────
    private fun gradesCreated(p: Payload.GradesCreated): AiChatItem.ToolCall {
        val total = p.createdCount + p.updatedCount
        val parts = buildList {
            if (p.createdCount > 0) add("${p.createdCount} mới")
            if (p.updatedCount > 0) add("${p.updatedCount} cập nhật")
            if (p.skippedCount > 0) add("${p.skippedCount} bị bỏ qua")
        }
        return AiChatItem.ToolCall(
            name = "create_grades",
            success = true,
            title = "Đã ghi điểm $total học phần",
            subtitle = parts.joinToString(" • ").ifBlank { null },
        )
    }

    // ─────────────────────────────── get_grades ────────────────────────
    private fun gradesViewed(p: Payload.GradesViewed): AiChatItem.ToolCall {
        val scope = p.semesterFilter?.let { semesterLabel(it) } ?: "tất cả học kỳ"
        val title = if (p.totalCourses == 0) {
            "Chưa có kết quả học tập"
        } else {
            "Đã xem kết quả học tập ($scope)"
        }
        val subtitle = if (p.totalCourses == 0) {
            "Chưa nhập điểm môn nào"
        } else {
            listOfNotNull(
                p.cpa?.let { "CPA %.2f".format(it) },
                p.classification,
                "${p.totalCourses} học phần",
            ).joinToString(" • ").ifBlank { null }
        }
        return AiChatItem.ToolCall(
            name = "get_grades",
            success = true,
            title = title,
            subtitle = subtitle,
        )
    }

    // ─────────────────────────────── search_tasks ─────────────────────
    private fun tasksFound(p: Payload.TasksFound): AiChatItem.ToolCall {
        val typeLabel = p.taskType?.let { taskTypeLabel(it) }
        val title = if (p.count == 0) "Không tìm thấy ${typeLabel ?: "công việc"} phù hợp"
        else "Đã tìm thấy ${p.count} ${typeLabel ?: "công việc"}"
        val statusLabel = when (p.status?.uppercase()) {
            "PENDING" -> "chưa xong"
            "COMPLETED" -> "đã xong"
            else -> null
        }
        val subtitle = listOfNotNull(
            p.query?.let { "Từ khoá: \"$it\"" },
            statusLabel,
        ).joinToString(" • ").ifBlank { if (p.count > 0) "Sắp xếp theo thời gian" else null }
        return AiChatItem.ToolCall("search_tasks", true, title, subtitle)
    }

    // ──────────────────────────────── get_task ────────────────────────
    private fun taskRead(p: Payload.TaskRead): AiChatItem.ToolCall {
        val typeLabel = taskTypeLabel(p.taskType)
        val deadlineLabel = (p.startTimeIso ?: p.endTimeIso)?.let { formatDeadline(it) }
        val subtitle = listOfNotNull(
            if (p.isCompleted) "đã xong" else null,
            deadlineLabel,
        ).joinToString(" • ").ifBlank { null }
        return AiChatItem.ToolCall(
            name = "get_task",
            success = true,
            title = "Đã xem $typeLabel: ${p.title ?: "(không tiêu đề)"}",
            subtitle = subtitle,
            tags = p.tags.map { AiChatItem.ToolCall.TagChip(it.name, it.colorHex) },
        )
    }

    // ──────────────────────────────── get_tags ────────────────────────
    private fun tagsListed(p: Payload.TagsListed): AiChatItem.ToolCall = AiChatItem.ToolCall(
        name = "get_tags",
        success = true,
        title = if (p.count == 0) "User chưa có nhãn nào" else "Đã lấy ${p.count} nhãn",
        subtitle = null,
    )

    private fun taskTypeLabel(kind: TaskKind): String = when (kind) {
        TaskKind.CLASS -> "lịch học"
        TaskKind.EXAM -> "lịch thi"
        TaskKind.TODO -> "việc cần làm"
    }

    /** "20251" → "HK1 · 2025". Trả nguyên mã nếu không parse được. */
    private fun semesterLabel(code: String): String {
        if (code.length < 5) return if (code.isBlank()) "" else "Kỳ $code"
        val year = code.substring(0, 4)
        val term = code.substring(4)
        val termLabel = when (term) {
            "1" -> "HK1"
            "2" -> "HK2"
            "3" -> "HK hè"
            else -> "HK$term"
        }
        return "$termLabel · $year"
    }

    // ─────────────────── fallback: tool fail không payload / tool lạ ────
    private fun fallback(call: AiToolCall): AiChatItem.ToolCall {
        if (!call.success) {
            val title = when (call.name) {
                "create_task" -> "Không tạo được task"
                "create_weekly_tasks" -> "Không tạo được task lặp tuần"
                "search_emails" -> "Tìm email thất bại"
                "get_email" -> "Không đọc được email"
                "search_news" -> "Tìm tin tức thất bại"
                "get_news" -> "Không đọc được tin"
                "create_grade" -> "Không ghi được điểm"
                "create_grades" -> "Không ghi được bảng điểm"
                "get_grades" -> "Không xem được kết quả học tập"
                "search_tasks" -> "Không xem được danh sách công việc"
                "get_task" -> "Không xem được chi tiết công việc"
                "get_tags" -> "Không lấy được danh sách nhãn"
                else -> "Lỗi khi chạy: ${call.name}"
            }
            val knownTool = call.name in KNOWN_FAILING_TOOLS
            return AiChatItem.ToolCall(
                name = call.name,
                success = false,
                title = title,
                errorMessage = if (knownTool) (call.error ?: "Lỗi không xác định") else call.error,
            )
        }
        // tool không nhận diện được nhưng chạy thành công
        return AiChatItem.ToolCall(
            name = call.name,
            success = true,
            title = "Đã chạy: ${call.name}",
            errorMessage = call.error,
        )
    }

    // ─────────────────────────── date/time format ──────────────────────

    private fun formatDeadline(iso: String): String? = try {
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleaned = iso.replace(Regex("\\.\\d+"), "")
        val date = src.parse(cleaned)
        if (date == null) null else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date)
    } catch (_: Throwable) {
        null
    }

    private fun formatDateShort(iso: String): String? = try {
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val cleaned = iso.replace(Regex("\\.\\d+"), "")
        val date = src.parse(cleaned)
        if (date == null) null else SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
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

    private val KNOWN_FAILING_TOOLS = setOf(
        "create_task", "create_weekly_tasks",
        "search_emails", "get_email", "search_news", "get_news",
        "create_grade", "create_grades", "get_grades",
        "search_tasks", "get_task", "get_tags",
    )
}
