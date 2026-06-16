package com.project.hustassistant.ui.aichat

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.project.hustassistant.ui.common.applyWindowInsets
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.hustassistant.TodoApplication
import com.project.hustassistant.data.ai.ChatHistoryRepository
import com.project.hustassistant.data.ai.ChatSessionSummary
import com.project.hustassistant.databinding.ActivityChatHistoryBinding
import kotlinx.coroutines.launch

/**
 * ChatHistoryActivity — danh sách các đoạn chat AI đã lưu local. MỚI 2026-06.
 *
 * - Tap 1 phiên  → mở lại đúng phiên đó trong AiChatActivity (resume by id) để tiếp tục.
 * - Nhấn giữ     → xoá phiên (kèm toàn bộ tin nhắn).
 *
 * Mở từ nút "lịch sử" trên header màn AI Chat.
 */
class ChatHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatHistoryBinding
    private lateinit var adapter: ChatHistoryAdapter

    private val repo by lazy { (application as TodoApplication).container.chatHistoryRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, binding.appBar)

        adapter = ChatHistoryAdapter(onTap = ::openSession, onDelete = ::confirmDelete)
        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
    }

    // Reload mỗi lần quay lại (vd vừa chat thêm xong).
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val sessions = repo.listSummaries()
            adapter.submitList(sessions)
            binding.tvEmpty.isVisible = sessions.isEmpty()
        }
    }

    private fun openSession(s: ChatSessionSummary) {
        val ctx = when (s.contextType) {
            ChatHistoryRepository.TYPE_EMAIL -> ChatContext.Email(s.contextKey, null)
            ChatHistoryRepository.TYPE_NEWS -> ChatContext.News(s.contextKey)
            else -> ChatContext.Standalone
        }
        AiChatActivity.startResume(this, ctx, s.id)
        finish() // không giữ màn Lịch sử trong back stack
    }

    private fun confirmDelete(s: ChatSessionSummary) {
        AlertDialog.Builder(this)
            .setTitle("Xoá đoạn chat?")
            .setMessage("Toàn bộ tin nhắn trong đoạn chat này sẽ bị xoá khỏi máy.")
            .setNegativeButton("Huỷ", null)
            .setPositiveButton("Xoá") { _, _ ->
                lifecycleScope.launch {
                    repo.deleteSession(s.id)
                    load()
                }
            }
            .show()
    }
}
