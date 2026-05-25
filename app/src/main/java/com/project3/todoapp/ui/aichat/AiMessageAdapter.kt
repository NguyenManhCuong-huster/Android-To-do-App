package com.project3.todoapp.ui.aichat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.ai.AiChatItem
import com.project3.todoapp.data.ai.AiMessage
import com.project3.todoapp.databinding.ItemAiMessageBinding
import com.project3.todoapp.databinding.ItemAiToolCallBinding

/**
 * AiMessageAdapter — render bubble tin nhắn AI/user + card tool call.
 *
 * View types:
 *   - VIEW_TYPE_MESSAGE   → item_ai_message.xml (bubble user/assistant như cũ)
 *   - VIEW_TYPE_TOOL_CALL → item_ai_tool_call.xml (card "AI đã làm gì")
 */
class AiMessageAdapter :
    ListAdapter<AiChatItem, RecyclerView.ViewHolder>(DiffCb()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AiChatItem.Message  -> VIEW_TYPE_MESSAGE
        is AiChatItem.ToolCall -> VIEW_TYPE_TOOL_CALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MESSAGE   -> MessageVH(ItemAiMessageBinding.inflate(inflater, parent, false))
            VIEW_TYPE_TOOL_CALL -> ToolCallVH(ItemAiToolCallBinding.inflate(inflater, parent, false))
            else                -> error("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AiChatItem.Message  -> (holder as MessageVH).bind(item.message)
            is AiChatItem.ToolCall -> (holder as ToolCallVH).bind(item)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Message bubble
    // ─────────────────────────────────────────────────────────────
    inner class MessageVH(private val b: ItemAiMessageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: AiMessage) {
            val isUser = msg.role == AiMessage.Role.USER
            b.bubbleUser.isVisible = isUser
            b.bubbleAssistant.isVisible = !isUser
            if (isUser) b.tvUser.text = msg.content
            else        b.tvAssistant.text = msg.content
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tool call card
    // ─────────────────────────────────────────────────────────────
    inner class ToolCallVH(private val b: ItemAiToolCallBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: AiChatItem.ToolCall) {
            b.tvIcon.text  = if (t.success) "✅" else "⚠️"
            b.tvTitle.text = t.title

            b.tvSubtitle.isVisible = !t.subtitle.isNullOrBlank()
            b.tvSubtitle.text      = t.subtitle.orEmpty()

            // Error message: chỉ show khi fail và có message
            val showError = !t.success && !t.errorMessage.isNullOrBlank()
            b.tvError.isVisible = showError
            b.tvError.text      = t.errorMessage.orEmpty()

            // Tag chips: clear + render từng tag với màu nền
            b.tagsContainer.removeAllViews()
            b.tagsScroll.isVisible = t.tags.isNotEmpty()
            val ctx = b.tagsContainer.context
            for (tag in t.tags) {
                val chip = TextView(ctx).apply {
                    text = tag.name
                    setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 12).toFloat()
                        setColor(parseColorOrFallback(tag.colorHex))
                    }
                    val lp = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd  = dp(ctx, 4)
                        topMargin  = dp(ctx, 4)
                    }
                    layoutParams = lp
                }
                b.tagsContainer.addView(chip)
            }
        }

        private fun parseColorOrFallback(hex: String?): Int = try {
            if (hex.isNullOrBlank()) FALLBACK_TAG_COLOR
            else Color.parseColor(hex)
        } catch (_: Throwable) { FALLBACK_TAG_COLOR }

        private fun dp(ctx: android.content.Context, v: Int): Int =
            (v * ctx.resources.displayMetrics.density).toInt()
    }

    // ─────────────────────────────────────────────────────────────
    // DiffUtil
    // ─────────────────────────────────────────────────────────────
    class DiffCb : DiffUtil.ItemCallback<AiChatItem>() {
        // Items không có id ổn định → identity check theo === (đảm bảo
        // append-only flow). Khi list rebuild, identity sẽ khác → DiffUtil
        // coi là item mới (đúng — UI animation hợp lý).
        override fun areItemsTheSame(a: AiChatItem, b: AiChatItem)   = a === b
        override fun areContentsTheSame(a: AiChatItem, b: AiChatItem) = a == b
    }

    companion object {
        private const val VIEW_TYPE_MESSAGE   = 1
        private const val VIEW_TYPE_TOOL_CALL = 2
        private const val FALLBACK_TAG_COLOR  = 0xFF607D8B.toInt()  // blue-grey
    }
}
