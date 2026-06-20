package com.project.hustassistant.data.ai.network

import com.project.hustassistant.network.ApiResponse
import okhttp3.MultipartBody
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

/**
 * 1 sự kiện SSE từ các endpoint /chat/stream, /email-chat/stream, /news-chat/stream.
 *
 * Wire: mỗi dòng `data: <json>` với field `type`:
 *   - "delta" → text         : 1 đoạn text mới của câu trả lời.
 *   - "tool"  → tool_call     : 1 tool vừa chạy xong.
 *   - "done"  → reply, references, usage, tool_calls : kết thúc (reply là text đầy đủ).
 *   - "error" → message       : lỗi.
 */
data class AiStreamChunkDto(
    val type: String,
    val text: String? = null,
    val tool_call: AiToolCallDto? = null,
    val reply: String? = null,
    val references: List<AiReferenceDto>? = null,
    val tool_calls: List<AiToolCallDto>? = null,
    val message: String? = null,
)

/** Response của /api/ai/upload-attachment. */
data class AiUploadAttachmentDto(
    val id: String,
    val file_name: String,
    val mime_type: String?,
    val size_bytes: Long?,
)

interface AiApi {

    /**
     * Upload 1 file vào AI Chat (owner_type='AI_CHAT' ở server).
     * Multipart field name = "file".
     *
     * (Chat thực hiện qua streaming SSE trong AiRepository, không qua Retrofit.)
     */
    @Multipart
    @POST("api/ai/upload-attachment")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
    ): ApiResponse<AiUploadAttachmentDto>
}
