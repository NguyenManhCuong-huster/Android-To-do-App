package com.project.hustassistant.data.ai

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.project.hustassistant.data.ai.network.AiApi
import com.project.hustassistant.data.ai.network.AiChatBody
import com.project.hustassistant.data.ai.network.AiEmailChatBody
import com.project.hustassistant.data.ai.network.AiMessageBody
import com.project.hustassistant.data.ai.network.AiNewsChatBody
import com.project.hustassistant.data.ai.network.AiReferenceDto
import com.project.hustassistant.data.ai.network.AiStreamChunkDto
import com.project.hustassistant.data.ai.network.AttachmentRefBody
import com.project.hustassistant.network.ServerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale


data class AiMessage(
    val role: Role,
    val content: String,
    val attachments: List<AttachmentRef> = emptyList(),
    val references: List<AiReference> = emptyList(),
) {
    enum class Role { USER, ASSISTANT }
}

class AiRepository(
    private val aiApi: AiApi,
    private val contentResolver: ContentResolver,
    private val streamingClient: OkHttpClient,
    private val serverConfig: ServerConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val gson = Gson()

    // ═══════════════════════════════════════════════════════════
    //  Streaming chat (SSE) — kênh DUY NHẤT để chat với AI.
    //
    //  Retrofit + Gson không stream tốt nên dùng OkHttp trực tiếp đọc body theo
    //  dòng. streamingClient đã có dynamicUrl + auth interceptor → chỉ cần build
    //  URL từ ServerConfig. Trả về Flow<AiStreamEvent> (Delta / Tool / Done / Error).
    // ═══════════════════════════════════════════════════════════

    fun chatStream(history: List<AiMessage>, systemInstruction: String? = null): Flow<AiStreamEvent> =
        streamRequest(
            "api/ai/chat/stream",
            gson.toJson(AiChatBody(history.map { it.toBody() }, systemInstruction)),
        )

    fun emailChatStream(emailId: String, history: List<AiMessage>): Flow<AiStreamEvent> =
        streamRequest(
            "api/ai/email-chat/stream",
            gson.toJson(AiEmailChatBody(emailId, history.map { it.toBody() })),
        )

    fun newsChatStream(newsId: String, history: List<AiMessage>): Flow<AiStreamEvent> =
        streamRequest(
            "api/ai/news-chat/stream",
            gson.toJson(AiNewsChatBody(newsId, history.map { it.toBody() })),
        )

    private fun streamRequest(path: String, bodyJson: String): Flow<AiStreamEvent> = flow {
        val url = serverConfig.getBaseUrl() + path
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        streamingClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val msg = response.body?.string()?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                emit(AiStreamEvent.Error(msg))
                return@flow
            }
            val source = response.body?.source()
            if (source == null) {
                emit(AiStreamEvent.Error("Server trả response rỗng."))
                return@flow
            }

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data.isEmpty() || data == "[DONE]") continue

                val chunk = runCatching { gson.fromJson(data, AiStreamChunkDto::class.java) }.getOrNull()
                    ?: continue

                when (chunk.type) {
                    "delta" -> chunk.text?.let { emit(AiStreamEvent.Delta(it)) }
                    "tool"  -> chunk.tool_call?.let { emit(AiStreamEvent.Tool(AiToolCallMapper.map(it))) }
                    "done"  -> {
                        emit(
                            AiStreamEvent.Done(
                                reply = chunk.reply.orEmpty(),
                                references = chunk.references.orEmpty().mapNotNull { it.toRefModel() },
                            ),
                        )
                        return@flow
                    }
                    "error" -> {
                        emit(AiStreamEvent.Error(chunk.message ?: "AI lỗi"))
                        return@flow
                    }
                }
            }
        }
    }.flowOn(dispatcher)

    /**
     * Upload 1 file từ content Uri lên server. Trả về AttachmentRef để
     * ViewModel embed vào message.attachments khi user send.
     *
     * Đọc bytes vào RAM rồi gửi multipart — phù hợp file < ~25MB (cap server).
     * Với file lớn hơn, server sẽ trả 413.
     */
    suspend fun uploadAttachment(uri: Uri): Result<AttachmentRef> = runCatching {
        withContext(dispatcher) {
            // 1) Read bytes
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Không đọc được file đã chọn.")
            if (bytes.isEmpty()) error("File rỗng.")

            // 2) Detect display name + mime
            val displayName = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
            val mime = contentResolver.getType(uri)
                ?: guessMimeFromName(displayName)
                ?: "application/octet-stream"

            // 3) Build multipart
            val reqBody = bytes.toRequestBody(mime.toMediaTypeOrNull(), 0, bytes.size)
            val part = MultipartBody.Part.createFormData("file", displayName, reqBody)

            // 4) Call API
            val res = aiApi.uploadAttachment(part)
            require(res.success) { res.message ?: "Upload thất bại" }
            val data = res.data ?: error("Server trả response rỗng.")

            AttachmentRef(
                id = data.id,
                fileName = data.file_name,
                mimeType = data.mime_type,
                sizeBytes = data.size_bytes,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            if (!it.moveToFirst()) return null
            return it.getString(idx)?.trim()?.takeIf { s -> s.isNotEmpty() }
        }
    }

    private fun guessMimeFromName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private fun AiMessage.toBody() = AiMessageBody(
        role = if (role == AiMessage.Role.USER) "user" else "assistant",
        content = content,
        attachments = attachments
            .takeIf { it.isNotEmpty() }
            ?.map { AttachmentRefBody(it.id, it.fileName, it.mimeType, it.sizeBytes) },
    )

    // ─────────────────────────────────────────────────────────────
    //  DTO → model mapping
    //
    //  Refactor GĐ1: tool-call → UI đã chuyển sang AiToolCallMapper (data)
    //  + AiToolCallPresenter (ui). Ở đây chỉ còn map reference.
    // ─────────────────────────────────────────────────────────────
    /** DTO reference (server) -> model UI. Bỏ entry thiếu/sai type. */
    private fun AiReferenceDto.toRefModel(): AiReference? {
        val t = type?.lowercase()?.takeIf { it == "email" || it == "news" } ?: return null
        val i = id?.takeIf { it.isNotBlank() } ?: return null
        return AiReference(type = t, id = i, label = label.orEmpty())
    }

    private companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
