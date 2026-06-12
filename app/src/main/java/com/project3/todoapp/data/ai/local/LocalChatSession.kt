package com.project3.todoapp.data.ai.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocalChatSession — 1 phiên chat AI được lưu local (Room). MỚI 2026-06.
 *
 * Mỗi (contextType, contextKey) có 1 phiên gần nhất để "tiếp tục":
 *   - EMAIL      → contextKey = emailId
 *   - NEWS       → contextKey = newsId
 *   - STANDALONE → contextKey = "standalone"
 */
@Entity(tableName = "chat_sessions")
data class LocalChatSession(
    @PrimaryKey val id: String,
    val contextType: String,
    val contextKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)
