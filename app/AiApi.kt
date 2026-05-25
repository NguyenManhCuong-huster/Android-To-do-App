package com.project3.todoapp.data.ai.network

import com.project3.todoapp.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AiApi — gọi tới /api/ai trên server. Server tự bọc Gemini API.
 *
 * Quy ước message: { role: "user" | "assistant", content: "..." }.
 *
 * THAY ĐỔI 2025-05-04:
 *   Response thêm field `tool_calls` — list các action AI đã thực hiện
 *   (vd tạo task qua function calling). Client dùng để hiển thị process
 *   trong chat UI.
 */

data class AiMessageBody(
    val role: String,    // "user" hoặc "assistant"
    val content: String,
)

data class AiChatBody(
    val messages: List<AiMessageBody>,
    val system_instruction: String? = null,
)

data class AiEmailChatBody(
    val email_id: String,
    val messages: List<AiMessageBody>,
)

/**
 * 1 entry trong response.tool_calls.
 *
 *   name   = "create_task"
 *   args   = { title, description, start_time, end_time, task_type, tag_ids }
 *   result = { success, task: { id, title, ... , tags: [...] } }
 *            hoặc { success: false, error: "..." }
 *
 * Dùng Map<String, Any?> cho linh hoạt — Gson tự parse JSON object thành
 * Map<String, *>, JSON array thành List<*>. ViewModel sẽ extract field cần.
 */
data class AiToolCallDto(
    val name: String,
    val args: Map<String, Any?>? = null,
    val result: Map<String, Any?>? = null,
)

data class AiChatReply(
    val reply: String,
    val thread_message_count: Int? = null,
    val usage: Map<String, Any?>? = null,
    val tool_calls: List<AiToolCallDto>? = null,
)

interface AiApi {

    /** Chat thuần — không có context kèm. */
    @POST("api/ai/chat")
    suspend fun chat(@Body body: AiChatBody): ApiResponse<AiChatReply>

    /** Chat có context là 1 thread email (server tự load thread). */
    @POST("api/ai/email-chat")
    suspend fun emailChat(@Body body: AiEmailChatBody): ApiResponse<AiChatReply>
}
