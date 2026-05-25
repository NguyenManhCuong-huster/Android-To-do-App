package com.project3.todoapp.data.task.network

import com.project3.todoapp.network.ApiResponse
import com.project3.todoapp.network.PagedResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TaskApi — endpoint REST của Task.
 *
 * Đặt cùng package với [TaskNetworkDataSource] vì nó chỉ phục vụ feature Task.
 * Repository ([com.project3.todoapp.data.task.DefaultTaskRepository]) KHÔNG import
 * file này — chỉ TaskNetworkDataSource gọi tới Retrofit interface ở đây.
 *
 * THAY ĐỔI 2026-05-07:
 *  Thêm priority + location (latitude, longitude, address_name) vào DTO sau khi
 *  server schema migrate (xem 003_add_task_priority_location.sql).
 *  Server priority: SMALLINT 1..4 (1=LOW, 2=MEDIUM, 3=HIGH, 4=URGENT).
 */

// ─── DTOs ──────────────────────────────────────────────
data class TaskDto(
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
    val tags: List<TagDtoRef>?,
)

/**
 * TagDtoRef — view nhỏ của tag, chỉ dùng làm sub-object trong TaskDto.
 *
 * Cố tình tách khỏi TagDto đầy đủ (ở data/tag/network) để Task module không
 * phải phụ thuộc ngược vào Tag module ở tầng network.
 */
data class TagDtoRef(
    val id: String,
    val name: String,
    val color_hex: String?,
)

data class CreateTaskBody(
    val title: String,
    val description: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val task_type: String? = "TODO",
    val source_type: String? = "MANUAL",
    val source_id: String? = null,
    val priority: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address_name: String? = null,
    val tag_ids: List<String> = emptyList(),
)

data class UpdateTaskBody(
    val title: String,
    val description: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val task_type: String? = "TODO",
    val is_completed: Boolean = false,
    val source_type: String? = "MANUAL",
    val source_id: String? = null,
    val priority: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address_name: String? = null,
    val tag_ids: List<String> = emptyList(),
)

// ─── API ───────────────────────────────────────────────
interface TaskApi {
    @GET("api/tasks")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 500,
        @Query("sort") sort: String = "mod_time",
        @Query("order") order: String = "asc",
        @Query("include_deleted") includeDeleted: Boolean = true,
    ): PagedResponse<TaskDto>

    @GET("api/tasks/{id}")
    suspend fun getById(@Path("id") id: String): ApiResponse<TaskDto>

    @POST("api/tasks")
    suspend fun create(@Body body: CreateTaskBody): ApiResponse<TaskDto>

    @PUT("api/tasks/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateTaskBody,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<TaskDto>

    @PATCH("api/tasks/{id}")
    suspend fun patch(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<TaskDto>

    @DELETE("api/tasks/{id}")
    suspend fun delete(
        @Path("id") id: String,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<Unit>
}
