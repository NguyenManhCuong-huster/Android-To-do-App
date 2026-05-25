package com.project3.todoapp.data.tag.network

import com.project3.todoapp.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TagApi — endpoint REST của Tag.
 *
 * Đặt cùng package với [NetworkTagSource], cùng feature data/tag/.
 * Repository [com.project3.todoapp.data.tag.DefaultTagRepository] không import file này.
 */

// ─── DTOs ──────────────────────────────────────────────
data class TagDto(
    val id: String,
    val name: String,
    val color_hex: String?,
    val is_deleted: Boolean?,
    val mod_time: String?,
)

data class CreateTagBody(
    val name: String,
    val color_hex: String? = null,
)

data class UpdateTagBody(
    val name: String? = null,
    val color_hex: String? = null,
)

// ─── API ───────────────────────────────────────────────
interface TagApi {
    @GET("api/tags")
    suspend fun list(
        @Query("include_deleted") includeDeleted: Boolean = true,
    ): ApiResponse<List<TagDto>>

    @POST("api/tags")
    suspend fun create(@Body body: CreateTagBody): ApiResponse<TagDto>

    @PUT("api/tags/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateTagBody,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<TagDto>

    @DELETE("api/tags/{id}")
    suspend fun delete(
        @Path("id") id: String,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<Unit>
}
