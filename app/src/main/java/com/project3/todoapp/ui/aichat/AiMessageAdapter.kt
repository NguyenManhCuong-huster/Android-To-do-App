package com.project3.todoapp.ui.aichat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.R
import com.project3.todoapp.data.ai.AiChatItem
import com.project3.todoapp.data.ai.AiMessage
import com.project3.todoapp.data.ai.AiReference
import com.project3.todoapp.data.ai.AttachmentRef
import com.project3.todoapp.databinding.ItemAiMessageBinding
import com.project3.todoapp.databinding.ItemAiToolCallBinding

/**
 * AiMessageAdapter — render bubble tin nhắn AI/user + card tool call.
 *
 * View types:
 *   - VIEW_TYPE_MESSAGE   → item_ai_message.xml (bubble user/assistant)
 *   - VIEW_TYPE_TOOL_CALL → item_ai_tool_call.xml (card "AI đã làm gì")
 *
 * THAY ĐỔI 2026-05-31:
 *  - Constructor nhận [onAttachmentTap] callback.
 *  - MessageVH render chip attachment trong bubble (cả user và assistant).
 *  - ToolCallVH dùng emoji 📎 cho tool `read_attachment` (success);
 *    còn lại giữ ✅/⚠️ như bản gốc.
 *
 * THAY ĐỔI 2026-06:
 *  - Constructor nhận thêm [onReferenceTap] callback.
 *  - Bubble assistant: token trích dẫn [[email:id]] / [[news:id]] trong reply
 *    được render thành chip bấm được (buildMessageText). Khi có reference,
 *    bật LinkMovementMethod + tắt textIsSelectable để span bấm được hoạt động.
 */
class AiMessageAdapter(
    private val onAttachmentTap: (AttachmentRef) -> Unit,
    private val onReferenceTap: (AiReference) -> Unit,
) : ListAdapter<AiChatItem, RecyclerView.ViewHolder>(DiffCb()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AiChatItem.Message  -> VIEW_TYPE_MESSAGE
        is AiChatItem.ToolCall -> VIEW_TYPE_TOOL_CALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MESSAGE   -> MessageVH(
                ItemAiMessageBinding.inflate(inflater, parent, false),
                onAttachmentTap,
                onReferenceTap,
            )
            VIEW_TYPE_TOOL_CALL -> ToolCallVH(ItemAiToolCallBinding.inflate(inflater, parent, false))
            else                -> error("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AiChatItem.Message  -> (holder as MessageVH).bind(item)
            is AiChatItem.ToolCall -> (holder as ToolCallVH).bind(item)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Message bubble + attachment chips + reference chips
    // ─────────────────────────────────────────────────────────────
    class MessageVH(
        private val b: ItemAiMessageBinding,
        private val onAttachmentTap: (AttachmentRef) -> Unit,
        private val onReferenceTap: (AiReference) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AiChatItem.Message) {
            val isUser = item.role == AiMessage.Role.USER
            b.bubbleUser.isVisible      = isUser
            b.bubbleAssistant.isVisible = !isUser

            if (isUser) {
                b.tvUser.text = item.content
                bindAttachments(b.userAttachmentsContainer, item.attachments)
                // Container assistant bên kia ẩn → không cần clear
            } else {
                bindAssistantText(item)
                bindAttachments(b.assistantAttachmentsContainer, item.attachments)
            }
        }

        /**
         * Render text bubble assistant. Nếu có reference → thay token bằng chip
         * bấm được + bật LinkMovementMethod. Nếu không → giữ text chọn được như cũ.
         */
        private fun bindAssistantText(item: AiChatItem.Message) {
            if (item.references.isEmpty()) {
                b.tvAssistant.movementMethod = null
                b.tvAssistant.setTextIsSelectable(true)
                b.tvAssistant.text = item.content
            } else {
                // textIsSelectable + clickable span xung đột → tắt selectable khi có link.
                b.tvAssistant.setTextIsSelectable(false)
                b.tvAssistant.movementMethod = LinkMovementMethod.getInstance()
                b.tvAssistant.text = buildMessageText(item.content, item.references, onReferenceTap)
            }
        }

        private fun bindAttachments(container: LinearLayout, refs: List<AttachmentRef>) {
            container.removeAllViews()
            if (refs.isEmpty()) {
                container.visibility = View.GONE
                return
            }
            container.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(container.context)
            for (ref in refs) {
                val chip = inflater.inflate(
                    R.layout.item_ai_message_attachment_chip, container, false,
                )
                chip.findViewById<TextView>(R.id.tvChipFileName).text = ref.fileName
                chip.setOnClickListener { onAttachmentTap(ref) }
                container.addView(chip)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tool call card — giữ nguyên cách render gốc (emoji + tag chip programmatic)
    // ─────────────────────────────────────────────────────────────
    class ToolCallVH(
        private val b: ItemAiToolCallBinding,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(t: AiChatItem.ToolCall) {
            b.tvIcon.text  = iconFor(t)
            b.tvTitle.text = t.title

            b.tvSubtitle.isVisible = !t.subtitle.isNullOrBlank()
            b.tvSubtitle.text      = t.subtitle.orEmpty()

            // Error message: chỉ show khi fail và có message
            val showError = !t.success && !t.errorMessage.isNullOrBlank()
            b.tvError.isVisible = showError
            b.tvError.text      = t.errorMessage.orEmpty()

            // Tag chips: clear + render từng tag với màu nền (programmatic)
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
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = dp(ctx, 4)
                        topMargin = dp(ctx, 4)
                    }
                }
                b.tagsContainer.addView(chip)
            }
        }

        /**
         * Chọn emoji theo tool name + trạng thái:
         *   read_attachment success → 📎 (báo cho user biết AI vừa đọc file)
         *   tool khác success       → ✅
         *   fail                    → ⚠️
         */
        private fun iconFor(t: AiChatItem.ToolCall): String = when {
            !t.success                  -> "⚠️"
            t.name == "read_attachment" -> "📎"
            else                        -> "✅"
        }

        private fun parseColorOrFallback(hex: String?): Int = try {
            if (hex.isNullOrBlank()) FALLBACK_TAG_COLOR
            else Color.parseColor(hex)
        } catch (_: Throwable) { FALLBACK_TAG_COLOR }

        private fun dp(ctx: Context, v: Int): Int =
            (v * ctx.resources.displayMetrics.density).toInt()
    }

    // ─────────────────────────────────────────────────────────────
    // DiffUtil
    // ─────────────────────────────────────────────────────────────
    class DiffCb : DiffUtil.ItemCallback<AiChatItem>() {
        override fun areItemsTheSame(a: AiChatItem, b: AiChatItem)    = a === b
        override fun areContentsTheSame(a: AiChatItem, b: AiChatItem) = a == b
    }

    companion object {
        private const val VIEW_TYPE_MESSAGE   = 1
        private const val VIEW_TYPE_TOOL_CALL = 2
        private const val FALLBACK_TAG_COLOR  = 0xFF607D8B.toInt()  // blue-grey
    }
}
