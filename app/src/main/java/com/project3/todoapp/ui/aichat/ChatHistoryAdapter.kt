package com.project3.todoapp.ui.aichat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.ai.ChatHistoryRepository
import com.project3.todoapp.data.ai.ChatSessionSummary
import com.project3.todoapp.databinding.ItemChatSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatHistoryAdapter — render danh sách phiên chat. Tiêu đề/preview/biểu tượng
 * được suy ra ở tầng UI (adapter) từ nội dung thô do repository trả về.
 */
class ChatHistoryAdapter(
    private val onTap: (ChatSessionSummary) -> Unit,
    private val onDelete: (ChatSessionSummary) -> Unit,
) : ListAdapter<ChatSessionSummary, ChatHistoryAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChatSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemChatSessionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: ChatSessionSummary) {
            b.tvIcon.text = when (s.contextType) {
                ChatHistoryRepository.TYPE_EMAIL -> "📧"
                ChatHistoryRepository.TYPE_NEWS -> "📰"
                else -> "💬"
            }
            b.tvTitle.text = titleFor(s)
            b.tvPreview.text = firstLine(s.lastContent).orEmpty()
            b.tvTime.text = TIME_FMT.format(Date(s.updatedAt))

            b.root.setOnClickListener { onTap(s) }
            b.root.setOnLongClickListener { onDelete(s); true }
            b.btnDelete.setOnClickListener { onDelete(s) }
        }

        private fun titleFor(s: ChatSessionSummary): String {
            val raw = s.firstUserContent.orEmpty()
            val candidate = when (s.contextType) {
                ChatHistoryRepository.TYPE_NEWS ->
                    raw.lineSequence().firstOrNull { it.trimStart().startsWith("📌") }
                        ?.trimStart()?.removePrefix("📌")?.trim()
                        ?: firstLine(raw)

                ChatHistoryRepository.TYPE_EMAIL -> {
                    val l = firstLine(raw).orEmpty()
                    l.removePrefix("Tôi đang đọc email:").trim().ifBlank { l }
                }

                else -> firstLine(raw)
            }
            return candidate?.take(80)?.ifBlank { null } ?: defaultTitle(s.contextType)
        }

        private fun firstLine(text: String?): String? =
            text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(120)

        private fun defaultTitle(type: String): String = when (type) {
            ChatHistoryRepository.TYPE_EMAIL -> "Chat về email"
            ChatHistoryRepository.TYPE_NEWS -> "Chat về tin tức"
            else -> "Trò chuyện chung"
        }
    }

    private class Diff : DiffUtil.ItemCallback<ChatSessionSummary>() {
        override fun areItemsTheSame(a: ChatSessionSummary, b: ChatSessionSummary) = a.id == b.id
        override fun areContentsTheSame(a: ChatSessionSummary, b: ChatSessionSummary) = a == b
    }

    companion object {
        private val TIME_FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}
