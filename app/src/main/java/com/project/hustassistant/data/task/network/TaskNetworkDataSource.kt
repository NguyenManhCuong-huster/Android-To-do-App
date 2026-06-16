package com.project.hustassistant.data.task.network

import com.project.hustassistant.data.task.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * TaskNetworkDataSource — implementation thật của [NetworkDataSource], gọi REST qua [TaskApi].
 *
 * Chiến lược (giống NetworkTagSource):
 *  - loadTasks  → GET /api/tasks?include_deleted=true (KHÔNG lọc is_deleted ở client,
 *                 để repository.pullRemoteChanges biết tombstone).
 *  - createTask → POST /api/tasks. Server hiện không nhận client-supplied id → server cấp id mới,
 *                 caller (repository) sẽ reassignId qua TaskDAO.reassignId.
 *  - updateTask → PUT /api/tasks/{id} kèm header `x-client-mod-time` để server LWW
 *                 (server trả 409 CONFLICT_LWW nếu server.mod_time > client mod_time).
 *  - deleteTask → DELETE /api/tasks/{id} (soft delete trên server) kèm `x-client-mod-time`.
 *
 * Mọi method bắt exception, trả null/false/emptyList. Repository tự decide retry.
 *
 * THAY ĐỔI 2026-05-07:
 *  Sau khi server migrate schema (003_add_task_priority_location.sql), giờ đã sync đủ
 *  priority + latitude + longitude + address_name. Trước đây các field này local-only,
 *  repository phải workaround bảo tồn giá trị local khi pull về.
 */
class TaskNetworkDataSource(
    private val taskApi: TaskApi,
) : NetworkDataSource {

    override suspend fun loadTasks(): List<NetworkTask> = try {
        taskApi.list(page = 1, limit = 1000, includeDeleted = true)
            .data
            .map { it.toNetworkTask() }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun createTask(task: Task): NetworkTask? = try {
        taskApi.create(
            CreateTaskBody(
                title = task.title,
                description = task.description.ifBlank { null },
                start_time = toIso(task.start),
                end_time = toIso(task.end),
                task_type = "TODO",
                source_type = "MANUAL",
                priority = task.priority.value,
                latitude = task.latitude,
                longitude = task.longitude,
                address_name = task.addressName,
                tag_ids = task.tags.map { it.id },
            )
        ).data?.toNetworkTask()
    } catch (_: Exception) {
        null
    }

    override suspend fun updateTask(task: Task): NetworkTask? = try {
        taskApi.update(
            id = task.id,
            body = UpdateTaskBody(
                title = task.title,
                description = task.description.ifBlank { null },
                start_time = toIso(task.start),
                end_time = toIso(task.end),
                task_type = "TODO",
                is_completed = task.isCompleted,
                source_type = "MANUAL",
                priority = task.priority.value,
                latitude = task.latitude,
                longitude = task.longitude,
                address_name = task.addressName,
                tag_ids = task.tags.map { it.id },
            ),
            clientModTime = toIso(task.modTime),
        ).data?.toNetworkTask()
    } catch (_: Exception) {
        // 409 CONFLICT_LWW cũng rơi vào đây. Repository sẽ pull về để cập nhật local
        // (server thắng), local mất dirty-flag ở vòng PULL kế tiếp.
        null
    }

    override suspend fun deleteTask(id: String, clientModTime: Long): Boolean = try {
        taskApi.delete(id = id, clientModTime = toIso(clientModTime)).success
    } catch (_: Exception) {
        false
    }

    // ─── Helpers ─────────────────────────────────────────
    private fun TaskDto.toNetworkTask() = NetworkTask(
        id = id,
        title = title,
        description = description ?: "",
        isCompleted = is_completed,
        start = fromIso(start_time),
        end = fromIso(end_time),
        modTime = fromIso(mod_time),
        priority = priority ?: DEFAULT_PRIORITY,
        latitude = latitude,
        longitude = longitude,
        addressName = address_name,
        isDeleted = is_deleted,
    )

    private fun toIso(epochMs: Long): String? =
        if (epochMs <= 0L) null else isoFmt().format(Date(epochMs))

    private fun fromIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun isoFmt() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    companion object {
        // Khớp Priority.MEDIUM.value
        private const val DEFAULT_PRIORITY = 2
    }
}
