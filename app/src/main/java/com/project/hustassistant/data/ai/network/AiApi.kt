package com.project.hustassistant.data.ai.network

import com.project.hustassistant.network.ApiResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class AttachmentRefBody(
    val id: String,
    val file_name: String,
    val mime_type: String? = null,
    val size_bytes: Long? = null,
)

data class AiMessageBody(
    val role: String,                                              // "user" | "assistant"
    val content: String,
    val attachments: List<AttachmentRefBody>? = null,              // ← MỚI 2026-05-31
)

data class AiChatBody(
    val messages: List<AiMessageBody>,
    val system_instruction: String? = null,
)

data class AiEmailChatBody(
    val email_id: String,
    val messages: List<AiMessageBody>,
)

data class AiNewsChatBody(
    // ← MỚI 2026-05-31
    val news_id: String,
    val messages: List<AiMessageBody>,
)

/** 1 entry trong response.tool_calls. */
data class AiToolCallDto(
    val name: String,
    val args: Map<String, Any?>? = null,
    val result: Map<String, Any?>? = null,
)

/**
 * 1 entry trong response.references — MỚI 2026-06.
 *
 * Server (resolveReferences) trả kèm reply: mỗi reference ứng với 1 token
 * [[email:id]] / [[news:id]] còn lại trong reply (đã xác thực quyền + tồn tại).
 *
 * Wire format: { "type": "email" | "news", "id": "<uuid>", "label": "<subject/title>" }
 */
data class AiReferenceDto(
    val type: String?,
    val id: String?,
    val label: String?,
)

/** Mini DTO cho effective_attachments trong response. */
data class EffectiveAttachmentDto(
    val id: String,
    val file_name: String,
    val mime_type: String?,
    val size_bytes: Long?,
    val is_downloaded: Boolean?,
    val is_inline: Boolean?,
)

data class AiChatReply(
    val reply: String,
    val thread_message_count: Int? = null,
    val usage: Map<String, Any?>? = null,
    val tool_calls: List<AiToolCallDto>? = null,
    val effective_attachments: List<EffectiveAttachmentDto>? = null,           // ← MỚI
    val references: List<AiReferenceDto>? = null,                              // ← MỚI 2026-06
)

/** Response của /api/ai/upload-attachment. */
data class AiUploadAttachmentDto(
    val id: String,
    val file_name: String,
    val mime_type: String?,
    val size_bytes: Long?,
)

interface AiApi {

    /** Chat thuần — không có context kèm. */
    @POST("api/ai/chat")
    suspend fun chat(@Body body: AiChatBody): ApiResponse<AiChatReply>

    /** Chat có context là 1 thread email (server tự load thread). */
    @POST("api/ai/email-chat")
    suspend fun emailChat(@Body body: AiEmailChatBody): ApiResponse<AiChatReply>

    /** Chat có context là 1 news/plan (server tự load + đính kèm file của news). */
    @POST("api/ai/news-chat")                                                  // ← MỚI 2026-05-31
    suspend fun newsChat(@Body body: AiNewsChatBody): ApiResponse<AiChatReply>

    /**
     * Upload 1 file vào AI Chat (owner_type='AI_CHAT' ở server).
     * Multipart field name = "file".
     */
    @Multipart
    @POST("api/ai/upload-attachment")                                          // ← MỚI 2026-05-31
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
    ): ApiResponse<AiUploadAttachmentDto>
}
