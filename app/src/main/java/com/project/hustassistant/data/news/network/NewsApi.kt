package com.project.hustassistant.data.news.network

import com.project.hustassistant.data.attachment.network.AttachmentDto
import com.project.hustassistant.network.ApiResponse
import com.project.hustassistant.network.PagedResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * NewsApi — endpoint REST của News.
 *
 * Server: routes/news.js
 *  - GET  /api/news                       — list (filter ?kind=, ?tag=, ?q=, ?page=, ?limit=)
 *  - GET  /api/news/{id}                  — chi tiết
 *  - GET  /api/news/{id}/attachments      — lazy-load attachments
 *  - POST /api/news/scrape                — admin trigger; client KHÔNG gọi.
 *
 *  THÊM MỚI:
 *  - GET  /api/news/recommendations               — đề xuất cá nhân hoá
 *  - POST /api/news/recommendations/refresh       — force recompute
 *  - POST /api/news/recommendations/{id}/dismiss  — user ẩn 1 đề xuất
 *
 * Client KHÔNG có create/update/delete cho news. News là read-only ở phía client.
 *
 * NewsDto kèm `attachments: List<AttachmentDto>?` (server bulk-fetch sẵn) và
 * 3 field optional `score / reason / matched_keywords` (chỉ có khi response
 * từ /api/news/recommendations).
 */

data class NewsDto(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String?,
    val article_url: String?,
    val image_url: String?,
    val tag: String?,
    val published_at: String?,
    val mod_time: String,
    val source_name: String?,
    val attachments: List<AttachmentDto>? = null,

    // ─── MỚI: chỉ có khi response từ /api/news/recommendations ───
    val score: Float? = null,
    val reason: String? = null,
    val matched_keywords: List<String>? = null,
)

interface NewsApi {
    @GET("api/news")
    suspend fun list(
        @Query("kind") kind: String? = null,
        @Query("tag") tag: String? = null,
        @Query("q") q: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
    ): PagedResponse<NewsDto>

    @GET("api/news/{id}")
    suspend fun getById(@Path("id") id: String): ApiResponse<NewsDto>

    @GET("api/news/{id}/attachments")
    suspend fun getAttachments(@Path("id") id: String): ApiResponse<List<AttachmentDto>>

    // ─── MỚI: Recommendations ────────────────────────────────────
    @GET("api/news/recommendations")
    suspend fun listRecommendations(
        @Query("limit") limit: Int = 50,
    ): PagedResponse<NewsDto>

    @POST("api/news/recommendations/refresh")
    suspend fun refreshRecommendations(): ApiResponse<Unit>

    @POST("api/news/recommendations/{id}/dismiss")
    suspend fun dismissRecommendation(@Path("id") id: String): ApiResponse<Unit>
}
