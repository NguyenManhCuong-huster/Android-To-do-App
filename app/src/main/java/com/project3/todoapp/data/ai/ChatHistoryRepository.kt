package com.project3.todoapp.data.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project3.todoapp.data.ai.local.ChatHistoryDao
import com.project3.todoapp.data.ai.local.LocalChatMessage
import com.project3.todoapp.data.ai.local.LocalChatSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Tóm tắt 1 phiên cho màn Lịch sử (MỚI 2026-06).
 * firstUserContent / lastContent là nội dung THÔ — tầng UI (adapter) tự suy ra
 * tiêu đề/preview/biểu tượng.
 */
data class ChatSessionSummary(
    val id: String,
    val contextType: String,
    val contextKey: String,
    val firstUserContent: String?,
    val lastContent: String?,
    val updatedAt: Long,
    val messageCount: Int,
)

/**
 * ChatHistoryRepository — lưu/đọc lịch sử phiên chat AI ở local (Room). MỚI 2026-06.
 *
 * Dùng để "tiếp tục" hội thoại: mở lại 1 context (email/news/standalone) sẽ resume
 * phiên gần nhất + nạp lại các tin nhắn đã lưu; hoặc mở 1 phiên cụ thể từ màn Lịch sử.
 *
 * references/attachments serialize bằng Gson (cột TEXT) → không cần Room TypeConverter.
 */
class ChatHistoryRepository(
    private val dao: ChatHistoryDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val gson = Gson()

    /** Id phiên gần nhất cho (type, key), hoặc null nếu chưa có. */
    suspend fun latestSession(type: String, key: String): String? = withContext(dispatcher) {
        dao.latestSession(type, key)?.id
    }

    /** Nạp toàn bộ tin nhắn 1 phiên (đúng thứ tự). */
    suspend fun loadMessages(sessionId: String): List<AiMessage> = withContext(dispatcher) {
        dao.messages(sessionId).map { it.toAiMessage() }
    }

    /** Danh sách phiên (mới nhất trước) cho màn Lịch sử; bỏ phiên rỗng. */
    suspend fun listSummaries(): List<ChatSessionSummary> = withContext(dispatcher) {
        dao.allSessions().mapNotNull { s ->
            val count = dao.messageCount(s.id)
            if (count == 0) return@mapNotNull null
            ChatSessionSummary(
                id = s.id,
                contextType = s.contextType,
                contextKey = s.contextKey,
                firstUserContent = dao.firstUserContent(s.id),
                lastContent = dao.lastContent(s.id),
                updatedAt = s.updatedAt,
                messageCount = count,
            )
        }
    }

    /** Tạo phiên mới, trả về id. */
    suspend fun createSession(type: String, key: String): String = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsertSession(LocalChatSession(id = id, contextType = type, contextKey = key, createdAt = now, updatedAt = now))
        id
    }

    /** Thêm 1 tin nhắn vào phiên (caller cấp [seq] để giữ thứ tự, tránh race). */
    suspend fun appendMessage(sessionId: String, message: AiMessage, seq: Long) = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        dao.upsertMessage(
            LocalChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                seq = seq,
                role = if (message.role == AiMessage.Role.USER) ROLE_USER else ROLE_ASSISTANT,
                content = message.content,
                referencesJson = message.references.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                attachmentsJson = message.attachments.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                createdAt = now,
            ),
        )
        dao.touchSession(sessionId, now)
    }

    suspend fun deleteSession(sessionId: String) = withContext(dispatcher) {
        dao.deleteSession(sessionId)
    }

    // ── mapping ──────────────────────────────────────────────
    private fun LocalChatMessage.toAiMessage(): AiMessage = AiMessage(
        role = if (role == ROLE_USER) AiMessage.Role.USER else AiMessage.Role.ASSISTANT,
        content = content,
        attachments = attachmentsJson?.let {
            gson.fromJson<List<AttachmentRef>>(it, object : TypeToken<List<AttachmentRef>>() {}.type)
        }.orEmpty(),
        references = referencesJson?.let {
            gson.fromJson<List<AiReference>>(it, object : TypeToken<List<AiReference>>() {}.type)
        }.orEmpty(),
    )

    companion object {
        const val TYPE_STANDALONE = "STANDALONE"
        const val TYPE_EMAIL = "EMAIL"
        const val TYPE_NEWS = "NEWS"
        const val KEY_STANDALONE = "standalone"

        private const val ROLE_USER = "USER"
        private const val ROLE_ASSISTANT = "ASSISTANT"
    }
}
