package com.project.hustassistant.data.ai

import com.project.hustassistant.data.ai.AiToolCall.NewsKind
import com.project.hustassistant.data.ai.AiToolCall.Payload
import com.project.hustassistant.data.ai.AiToolCall.TagRef
import com.project.hustassistant.data.ai.AiToolCall.TaskKind
import com.project.hustassistant.data.ai.network.AiToolCallDto

/**
 * AiToolCallMapper — biên giới DUY NHẤT đọc "result" (shape do server/tool định
 * nghĩa) và đóng gói thành [AiToolCall] có kiểu.
 *
 * Vì mỗi tool trả về một shape khác nhau và shape do server quyết định (có thể
 * đổi), ta giữ một Map tolerant ở đúng MỘT chỗ này thay vì rải `as?` khắp
 * repository. Ở đây KHÔNG sinh chuỗi hiển thị, KHÔNG format ngày — đó là việc
 * của [com.project.hustassistant.ui.aichat.AiToolCallPresenter].
 *
 * (Bước nâng cấp sau — Giai đoạn 4: thay Map bằng ToolResultDto có kiểu nếu
 * muốn; mọi thứ phía trên mapper đã typed nên việc đó không lan ra ngoài.)
 */
object AiToolCallMapper {

    fun map(dto: AiToolCallDto): AiToolCall {
        val result = dto.result.orEmpty()
        val success = result["success"] as? Boolean ?: false
        val error = result["error"] as? String

        val payload: Payload? = when (dto.name) {
            "create_task" -> if (success) parseTaskCreated(result) else null
            "create_weekly_tasks" -> if (success) parseWeekly(result) else null
            "read_attachment" ->
                if (success) parseAttachmentRead(result) else parseAttachmentFailed(result)
            "search_emails" -> if (success) parseEmailsFound(result) else null
            "get_email" -> if (success) parseEmailGet(result) else null
            "search_news" -> if (success) parseNewsFound(result) else null
            "get_news" -> if (success) parseNewsRead(result) else null
            // web_search: giữ payload cả khi fail để Presenter còn query mà hiển thị.
            "web_search" -> parseWebSearch(result, dto.args)
            "create_grade" -> if (success) parseGradeCreated(result) else null
            "create_grades" -> if (success) parseGradesCreated(result) else null
            "get_grades" -> if (success) parseGradesViewed(result) else null
            "search_tasks" -> if (success) parseTasksFound(result) else null
            "get_task" -> if (success) parseTaskRead(result) else null
            "get_tags" -> if (success) parseTagsListed(result) else null
            else -> null
        }

        return AiToolCall(name = dto.name, success = success, error = error, payload = payload)
    }

    // ─────────────────────────────────────────────────────────────
    //  Parse từng tool — chỉ bóc dữ liệu, không tạo chữ hiển thị.
    // ─────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseTaskCreated(result: Map<String, Any?>): Payload.TaskCreated {
        val task = result["task"] as? Map<String, Any?> ?: emptyMap()
        return Payload.TaskCreated(
            title = (task["title"] as? String).orEmpty(),
            taskType = TaskKind.from(task["task_type"] as? String),
            endTimeIso = task["end_time"] as? String,
            tags = parseTags(task["tags"]),
        )
    }

    private fun parseWeekly(result: Map<String, Any?>): Payload.WeeklyTasksCreated {
        @Suppress("UNCHECKED_CAST")
        val s = result["summary"] as? Map<String, Any?> ?: emptyMap()
        return Payload.WeeklyTasksCreated(
            title = (s["title"] as? String).orEmpty(),
            created = (s["created"] as? Number)?.toInt() ?: 0,
            skipped = (s["skipped"] as? Number)?.toInt() ?: 0,
            taskType = TaskKind.from(s["task_type"] as? String),
            dayOfWeek = (s["day_of_week"] as? Number)?.toInt() ?: 0,
            firstStartIso = s["first_start_time"] as? String,
            lastStartIso = s["last_start_time"] as? String,
            firstEndIso = s["first_end_time"] as? String,
            lastEndIso = s["last_end_time"] as? String,
            tags = parseTags(s["tags"]),
        )
    }

    private fun parseAttachmentRead(result: Map<String, Any?>): Payload.AttachmentRead =
        Payload.AttachmentRead(
            fileName = result["file_name"] as? String,
            truncated = result["truncated"] as? Boolean ?: false,
            fromCache = result["from_cache"] as? Boolean ?: false,
        )

    private fun parseAttachmentFailed(result: Map<String, Any?>): Payload.AttachmentReadFailed {
        val avail = (result["available_files"] as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        return Payload.AttachmentReadFailed(availableFiles = avail)
    }

    private fun parseEmailsFound(result: Map<String, Any?>): Payload.EmailsFound =
        Payload.EmailsFound(
            count = (result["count"] as? Number)?.toInt() ?: 0,
            query = (result["query"] as? String)?.takeIf { it.isNotBlank() },
        )

    @Suppress("UNCHECKED_CAST")
    private fun parseEmailGet(result: Map<String, Any?>): Payload {
        // Hai shape: { email: {...} } (single) HOẶC { messages: [...], message_count }.
        val email = result["email"] as? Map<String, Any?>
        return if (email != null) {
            Payload.EmailRead(
                subject = (email["subject"] as? String)?.takeIf { it.isNotBlank() },
                from = (email["from"] as? String)?.takeIf { it.isNotBlank() },
                truncated = email["truncated"] as? Boolean ?: false,
            )
        } else {
            val messages = result["messages"] as? List<*>
            Payload.EmailThreadRead(
                messageCount = (result["message_count"] as? Number)?.toInt()
                    ?: messages?.size
                    ?: 0,
            )
        }
    }

    private fun parseNewsFound(result: Map<String, Any?>): Payload.NewsFound =
        Payload.NewsFound(
            count = (result["count"] as? Number)?.toInt() ?: 0,
            query = (result["query"] as? String)?.takeIf { it.isNotBlank() },
            kind = NewsKind.from((result["kind"] as? String)?.takeIf { it.isNotBlank() }),
        )

    private fun parseNewsRead(result: Map<String, Any?>): Payload.NewsRead {
        @Suppress("UNCHECKED_CAST")
        val news = result["news"] as? Map<String, Any?> ?: emptyMap()
        return Payload.NewsRead(
            title = (news["title"] as? String)?.takeIf { it.isNotBlank() },
            kind = NewsKind.from(news["kind"] as? String),
            tag = (news["tag"] as? String)?.takeIf { it.isNotBlank() },
            truncated = news["truncated"] as? Boolean ?: false,
        )
    }

    private fun parseWebSearch(result: Map<String, Any?>, args: Map<String, Any?>?): Payload.WebSearched =
        Payload.WebSearched(
            query = ((result["query"] as? String) ?: (args?.get("query") as? String))
                ?.takeIf { it.isNotBlank() },
            count = (result["count"] as? Number)?.toInt() ?: 0,
        )

    @Suppress("UNCHECKED_CAST")
    private fun parseGradeCreated(result: Map<String, Any?>): Payload.GradeCreated {
        val g = result["grade"] as? Map<String, Any?> ?: emptyMap()
        return Payload.GradeCreated(
            action = (result["action"] as? String).orEmpty(),
            semester = (g["semester"] as? String).orEmpty(),
            courseCode = (g["course_code"] as? String).orEmpty(),
            courseName = (g["course_name"] as? String).orEmpty(),
            letter = (g["letter_grade"] as? String)?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseGradesCreated(result: Map<String, Any?>): Payload.GradesCreated {
        val skipped = (result["skipped"] as? List<*>)?.size ?: 0
        return Payload.GradesCreated(
            createdCount = (result["created_count"] as? Number)?.toInt() ?: 0,
            updatedCount = (result["updated_count"] as? Number)?.toInt() ?: 0,
            skippedCount = skipped,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseGradesViewed(result: Map<String, Any?>): Payload.GradesViewed {
        val summary = result["summary"] as? Map<String, Any?> ?: emptyMap()
        val eff = result["effective_params"] as? Map<String, Any?> ?: emptyMap()
        return Payload.GradesViewed(
            cpa = (summary["cpa"] as? Number)?.toDouble(),
            classification = (summary["classification"] as? String)?.takeIf { it.isNotBlank() },
            totalCourses = (result["total_courses"] as? Number)?.toInt() ?: 0,
            semesterFilter = (eff["semester"] as? String)?.takeIf { it.isNotBlank() },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTasksFound(result: Map<String, Any?>): Payload.TasksFound {
        val eff = result["effective_params"] as? Map<String, Any?> ?: emptyMap()
        val typeRaw = (eff["task_type"] as? String)?.takeIf { it.isNotBlank() }
        return Payload.TasksFound(
            count = (result["count"] as? Number)?.toInt() ?: 0,
            query = (eff["query"] as? String)?.takeIf { it.isNotBlank() },
            taskType = typeRaw?.let { TaskKind.from(it) },
            status = (eff["status"] as? String)?.takeIf { it.isNotBlank() },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTaskRead(result: Map<String, Any?>): Payload.TaskRead {
        val task = result["task"] as? Map<String, Any?> ?: emptyMap()
        return Payload.TaskRead(
            title = (task["title"] as? String)?.takeIf { it.isNotBlank() },
            taskType = TaskKind.from(task["task_type"] as? String),
            isCompleted = task["is_completed"] as? Boolean ?: false,
            startTimeIso = task["start_time"] as? String,
            endTimeIso = task["end_time"] as? String,
            tags = parseTags(task["tags"]),
        )
    }

    private fun parseTagsListed(result: Map<String, Any?>): Payload.TagsListed =
        Payload.TagsListed(count = (result["count"] as? Number)?.toInt() ?: 0)

    @Suppress("UNCHECKED_CAST")
    private fun parseTags(raw: Any?): List<TagRef> =
        (raw as? List<Map<String, Any?>>).orEmpty().map {
            TagRef(name = (it["name"] as? String).orEmpty(), colorHex = it["color_hex"] as? String)
        }
}
