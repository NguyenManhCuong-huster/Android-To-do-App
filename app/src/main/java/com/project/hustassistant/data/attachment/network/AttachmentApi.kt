package com.project.hustassistant.data.attachment.network

import com.project.hustassistant.network.ApiResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * AttachmentApi — REST endpoints cho /api/attachments.
 *
 * Server: routes/attachments.js
 *   - GET /api/attachments/by-owner?owner_type=&owner_id=  — list metadata
 *   - GET /api/attachments/:id/meta                        — 1 row metadata
 *   - GET /api/attachments/:id/download                    — STREAM bytes
 *
 * Download dùng @Streaming + ResponseBody để không load hết vào RAM
 * (file có thể tới 25 MB).
 *
 * Lưu ý: với raw ResponseBody, Retrofit không apply gson; status code đọc qua
 * Response<ResponseBody>. Khi !isSuccessful → đọc errorBody().
 */

/**
 * DTO trả về từ server. Khớp với output của attachmentService.listForOwner().
 * Server dùng snake_case → giữ y nguyên ở DTO, mapper convert sang camelCase.
 */
data class AttachmentDto(
    val id: String,
    val owner_type: String,
    val owner_id: String,
    val file_name: String,
    val mime_type: String?,
    val size_bytes: Long?,
    val is_downloaded: Boolean,
    val source_url: String?,
    val is_inline: Boolean?,
    val mod_time: String?,
    val created_at: String?,
)

interface AttachmentApi {
    @GET("api/attachments/by-owner")
    suspend fun listByOwner(
        @Query("owner_type") ownerType: String,
        @Query("owner_id")   ownerId:   String,
    ): ApiResponse<List<AttachmentDto>>

    @GET("api/attachments/{id}/meta")
    suspend fun getMeta(@Path("id") id: String): ApiResponse<AttachmentDto>

    /**
     * Download bytes của 1 attachment. Caller phải đọc bằng `body().byteStream()`.
     *
     * QUAN TRỌNG: @Streaming bắt buộc — nếu không, OkHttp sẽ buffer toàn bộ
     * response vào RAM (OOM với file lớn).
     */
    @Streaming
    @GET("api/attachments/{id}/download")
    suspend fun download(@Path("id") id: String): Response<ResponseBody>
}
