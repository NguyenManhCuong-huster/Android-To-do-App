package com.project.hustassistant.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * SyncApi — đồng bộ BATCH 2 chiều với server qua /api/sync.
 *
 * Mô hình (chốt 2026-06):
 *  - Client sinh id cho bản ghi tạo offline, gửi LÊN; server DÙNG LUÔN id đó (không
 *    cấp id mới) → không bao giờ phải reassign.
 *  - Server là nơi DUY NHẤT quyết Last-Write-Wins, theo `mod_time` client gửi (so '>').
 *  - "Xóa tối thượng": server đã xoá thì bỏ qua mọi push tới (không hồi sinh).
 *  - Cross-ref (task–tag) KHÔNG sync riêng: mỗi task mang `tag_ids` (tập tag đang gắn),
 *    server tự đối chiếu trong transaction.
 *
 * PUSH:  POST /api/sync   body { tasks:[...], tags:[...] }
 * PULL:  GET  /api/sync?since=<ISO>  → { server_time, changes:{ tasks:[...], tags:[...] } }
 */

// ─── PUSH payload ──────────────────────────────────────
data class SyncPushBody(
    val tasks: List<SyncTaskPush>,
    val tags: List<SyncTagPush>,
)

data class SyncTaskPush(
    val id: String,
    val title: String,
    val description: String?,
    val start_time: String?,
    val end_time: String?,
    val task_type: String? = "TODO",
    val is_completed: Boolean = false,
    val source_type: String? = "MANUAL",
    val priority: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address_name: String? = null,
    val is_deleted: Boolean = false,
    val mod_time: String,
    val tag_ids: List<String> = emptyList(),
)

data class SyncTagPush(
    val id: String,
    val name: String,
    val color_hex: String?,
    val is_deleted: Boolean = false,
    val mod_time: String,
)

/** Server trả { success, server_time, results:{tasks,tags}, meta }. */
data class SyncPushResponse(
    val success: Boolean,
    val server_time: String?,
    val results: SyncPushResults?,
)

data class SyncPushResults(
    val tasks: List<SyncResult> = emptyList(),
    val tags: List<SyncResult> = emptyList(),
)

/** status: created | updated | deleted | skipped_deleted | stale | skipped | error */
data class SyncResult(
    val id: String?,
    val status: String?,
)

// ─── PULL payload ──────────────────────────────────────
data class SyncPullResponse(
    val success: Boolean,
    val server_time: String?,
    val changes: SyncChanges?,
)

data class SyncChanges(
    val tasks: List<SyncTaskPull> = emptyList(),
    val tags: List<SyncTagPull> = emptyList(),
)

data class SyncTaskPull(
    val id: String,
    val title: String,
    val description: String?,
    val start_time: String?,
    val end_time: String?,
    val task_type: String?,
    val is_completed: Boolean,
    val source_type: String?,
    val source_id: String?,
    val priority: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val address_name: String?,
    val is_deleted: Boolean,
    val mod_time: String,
    val tags: List<SyncTagRef>?,
)

data class SyncTagRef(
    val id: String,
    val name: String,
    val color_hex: String?,
)

data class SyncTagPull(
    val id: String,
    val name: String,
    val color_hex: String?,
    val is_deleted: Boolean,
    val mod_time: String,
)

interface SyncApi {
    @POST("api/sync")
    suspend fun push(@Body body: SyncPushBody): SyncPushResponse

    @GET("api/sync")
    suspend fun pull(@Query("since") since: String): SyncPullResponse
}
