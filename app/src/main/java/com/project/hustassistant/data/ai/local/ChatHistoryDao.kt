package com.project.hustassistant.data.ai.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * ChatHistoryDao — đọc/ghi phiên + tin nhắn chat. MỚI 2026-06.
 * Xoá session sẽ tự cascade xoá message (ForeignKey CASCADE).
 */
@Dao
interface ChatHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: LocalChatSession)

    @Query("UPDATE chat_sessions SET updatedAt = :ts WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, ts: Long)

    @Query(
        "SELECT * FROM chat_sessions WHERE contextType = :type AND contextKey = :key " +
            "ORDER BY updatedAt DESC LIMIT 1",
    )
    suspend fun latestSession(type: String, key: String): LocalChatSession?

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun allSessions(): List<LocalChatSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: LocalChatMessage)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY seq ASC")
    suspend fun messages(sessionId: String): List<LocalChatMessage>

    // ── cho màn Lịch sử (MỚI 2026-06) ─────────────────────────
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun messageCount(sessionId: String): Int

    @Query(
        "SELECT content FROM chat_messages WHERE sessionId = :sessionId AND role = 'USER' " +
            "ORDER BY seq ASC LIMIT 1",
    )
    suspend fun firstUserContent(sessionId: String): String?

    @Query("SELECT content FROM chat_messages WHERE sessionId = :sessionId ORDER BY seq DESC LIMIT 1")
    suspend fun lastContent(sessionId: String): String?

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAll()
}
