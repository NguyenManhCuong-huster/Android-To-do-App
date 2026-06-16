package com.project.hustassistant.ui.aichat

import android.content.Intent

/**
 * ChatContext — đối tượng mà phiên chat đang nói về.
 *
 * Sealed class để [AiChatViewModel] biết:
 *   • Bootstrap thế nào (load email thread / news cache / không seed gì cho Standalone).
 *   • Gọi endpoint AI nào (emailChat vs chat thường).
 *
 * THAY ĐỔI 2026-05-08:
 *   - Thêm [Standalone] cho chat tự do, không gắn với object cụ thể.
 *     Dùng cho card "AI Chat" ở MainActivity.
 *
 * Truyền qua Intent dạng 3 extras:
 *   EXTRA_KIND  → "EMAIL" | "NEWS" | "STANDALONE"
 *   EXTRA_ID    → emailId / newsId (Standalone không cần)
 *   EXTRA_LABEL → text optional cho header
 */
sealed class ChatContext {

    data class Email(val emailId: String, val subject: String?) : ChatContext()
    data class News(val newsId: String) : ChatContext()
    object Standalone : ChatContext()

    fun putInto(intent: Intent): Intent {
        when (this) {
            is Email -> intent
                .putExtra(EXTRA_KIND, KIND_EMAIL)
                .putExtra(EXTRA_ID, emailId)
                .putExtra(EXTRA_LABEL, subject)
            is News -> intent
                .putExtra(EXTRA_KIND, KIND_NEWS)
                .putExtra(EXTRA_ID, newsId)
            Standalone -> intent
                .putExtra(EXTRA_KIND, KIND_STANDALONE)
        }
        return intent
    }

    companion object {
        private const val EXTRA_KIND  = "chat_kind"
        private const val EXTRA_ID    = "chat_id"
        private const val EXTRA_LABEL = "chat_label"

        private const val KIND_EMAIL      = "EMAIL"
        private const val KIND_NEWS       = "NEWS"
        private const val KIND_STANDALONE = "STANDALONE"

        /** Đọc lại ChatContext từ Intent. Trả null nếu thiếu/sai key. */
        fun readFrom(intent: Intent): ChatContext? = when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_EMAIL -> {
                val id = intent.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() }
                id?.let { Email(it, intent.getStringExtra(EXTRA_LABEL)) }
            }
            KIND_NEWS -> {
                val id = intent.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() }
                id?.let { News(it) }
            }
            KIND_STANDALONE -> Standalone
            else            -> null
        }
    }
}
