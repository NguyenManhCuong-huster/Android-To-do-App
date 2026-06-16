package com.project.hustassistant.data.email.network

import com.project.hustassistant.data.attachment.network.AttachmentDto
import com.project.hustassistant.network.ApiResponse
import com.project.hustassistant.network.PagedResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * EmailApi.
 *
 * THAY ĐỔI 2025-05-02:
 *  - 1 row = 1 message Gmail.
 *
 * THAY ĐỔI 2026-05-23:
 *  - EmailDto giờ kèm `attachments: List<AttachmentDto>?` — server đã bulk-fetch
 *    sẵn để client không phải N+1.
 */

data class EmailDto(
    val id: String,
    val account_id: String,
    val gmail_message_id: String?,
    val gmail_thread_id: String?,
    val sender: String?,
    val recipient: String?,
    val subject: String?,
    val snippet: String?,
    val body_text: String?,
    val body_html: String?,
    val deep_link_intent: String?,
    val received_at: String?,
    val is_deleted: Boolean?,
    val mod_time: String?,
    val account_email: String?,
    val attachments: List<AttachmentDto>? = null,    // ← MỚI
)

data class ThreadDto(
    val thread_id: String,
    val messages: List<EmailDto>,
)

data class EmailSyncBody(val account_id: String? = null)

data class EmailSyncAccountResult(
    val account_id: String?,
    val email: String?,
    val status: String?,
    val new_emails: Int?,
    val since: String?,
    val error: String?,
    val attachments_downloaded: Int? = null,    // ← MỚI
    val attachments_failed:     Int? = null,
    val attachments_skipped:    Int? = null,
)

data class EmailSyncSummary(
    val synced: Int,
    val accounts: List<EmailSyncAccountResult>?,
    val message: String?,
)

interface EmailApi {
    @GET("api/emails")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("q") q: String? = null,
        @Query("account_id") accountId: String? = null,
    ): PagedResponse<EmailDto>

    @GET("api/emails/all")
    suspend fun listAll(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
    ): PagedResponse<EmailDto>

    @GET("api/emails/{id}")
    suspend fun getById(@Path("id") id: String): ApiResponse<EmailDto>

    @GET("api/emails/{id}/thread")
    suspend fun getThread(@Path("id") id: String): ApiResponse<ThreadDto>

    @GET("api/emails/{id}/attachments")
    suspend fun getAttachments(@Path("id") id: String): ApiResponse<List<AttachmentDto>>

    @DELETE("api/emails/{id}")
    suspend fun delete(@Path("id") id: String): ApiResponse<Unit>

    @POST("api/emails/sync")
    suspend fun sync(@Body body: EmailSyncBody = EmailSyncBody()): ApiResponse<EmailSyncSummary>
}
