package com.project3.todoapp.data.ai

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.project3.todoapp.data.ai.network.AiApi
import com.project3.todoapp.data.ai.network.AiChatBody
import com.project3.todoapp.data.ai.network.AiEmailChatBody
import com.project3.todoapp.data.ai.network.AiMessageBody
import com.project3.todoapp.data.ai.network.AiNewsChatBody
import com.project3.todoapp.data.ai.network.AiReferenceDto
import com.project3.todoapp.data.ai.network.AttachmentRefBody
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ═══════════════════════════════════════════════════════════
    //  Chat endpoints
    // ═══════════════════════════════════════════════════════════

    /**
     * Chat có context email: server tự fetch thread + attachments của thread.
     * Per-message attachments (nếu có) vẫn được gửi cho server validate +
     * merge vào "allowed IDs".
     */
    suspend fun emailChat(
        emailId: String,
        history: List<AiMessage>,
    ): Result<AiChatResult> = runCatching {
        withContext(dispatcher) {
            val res = aiApi.emailChat(
                AiEmailChatBody(
                    email_id = emailId,
                    messages = history.map { it.toBody() },
                )
            )
            require(res.success) { res.message ?: "AI request failed" }
            val data = res.data
            AiChatResult(
                reply = data?.reply.orEmpty(),
                toolCalls = data?.tool_calls.orEmpty().map { AiToolCallMapper.map(it) },
                references = data?.references.orEmpty().mapNotNull { it.toRefModel() },
            )
        }
    }

    /**
     * Chat có context news: server tự fetch news + attachments của news.
     * MỚI 2026-05-31.
     */
    suspend fun newsChat(
        newsId: String,
        history: List<AiMessage>,
    ): Result<AiChatResult> = runCatching {
        withContext(dispatcher) {
            val res = aiApi.newsChat(
                AiNewsChatBody(
                    news_id = newsId,
                    messages = history.map { it.toBody() },
                )
            )
            require(res.success) { res.message ?: "AI request failed" }
            val data = res.data
            AiChatResult(
                reply = data?.reply.orEmpty(),
                toolCalls = data?.tool_calls.orEmpty().map { AiToolCallMapper.map(it) },
                references = data?.references.orEmpty().mapNotNull { it.toRefModel() },
            )
        }
    }

    /** Chat thuần — không có email/news context. */
    suspend fun chat(
        history: List<AiMessage>,
        systemInstruction: String? = null,
    ): Result<AiChatResult> = runCatching {
        withContext(dispatcher) {
            val res = aiApi.chat(
                AiChatBody(
                    messages = history.map { it.toBody() },
                    system_instruction = systemInstruction,
                )
            )
            require(res.success) { res.message ?: "AI request failed" }
            val data = res.data
            AiChatResult(
                reply = data?.reply.orEmpty(),
                toolCalls = data?.tool_calls.orEmpty().map { AiToolCallMapper.map(it) },
                references = data?.references.orEmpty().mapNotNull { it.toRefModel() },
            )
        }
    }


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
}
