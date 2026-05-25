package com.project3.todoapp.data.news.network

import com.project3.todoapp.data.attachment.network.AttachmentDto
import com.project3.todoapp.network.ApiResponse
import com.project3.todoapp.network.PagedResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * NewsApi — endpoint REST của News.
 *
 * Server: routes/news.js
 *  - GET /api/news        — list (filter ?kind=, ?tag=, ?q=, ?page=, ?limit=)
 *  - GET /api/news/{id}   — chi tiết
 *  - GET /api/news/{id}/attachments — lazy-load attachments
 *  - POST /api/news/scrape — admin trigger; client KHÔNG gọi.
 *
 * Client KHÔNG có create/update/delete. News là read-only ở phía client.
 *
 * THAY ĐỔI 2026-05-23:
 *  - NewsDto giờ kèm `attachments: List<AttachmentDto>?` — server bulk-fetch sẵn.
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
    val attachments: List<AttachmentDto>? = null,    // ← MỚI
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
}
