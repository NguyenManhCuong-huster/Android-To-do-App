package com.project.hustassistant.ui.aichat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
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
import com.project.hustassistant.R
import com.project.hustassistant.data.ai.AiChatItem
import com.project.hustassistant.data.ai.AiMessage
import com.project.hustassistant.data.ai.AiReference
import com.project.hustassistant.data.ai.AttachmentRef
import com.project.hustassistant.databinding.ItemAiMessageBinding
import com.project.hustassistant.databinding.ItemAiToolCallBinding
import io.noties.markwon.Markwon

/**
 * AiMessageAdapter — render bubble tin nhắn AI/user + card tool call + bong bóng
 * đang stream.
 *
 * View types:
 *   - VIEW_TYPE_MESSAGE   → item_ai_message.xml (bubble user/assistant + copy/sửa)
 *   - VIEW_TYPE_TOOL_CALL → item_ai_tool_call.xml (card "AI đã làm gì")
 *   - VIEW_TYPE_THINKING  → item_ai_thinking.xml (3 chấm nhảy)
 *   - VIEW_TYPE_STREAMING → item_ai_streaming.xml (assistant đang gõ dần)
 *
 * THAY ĐỔI 2026-06 (streaming + markdown):
 *  - Reply assistant render MARKDOWN qua [Markwon].
 *  - Bong bóng assistant có nút Copy; bong bóng user có nút Sửa.
 *  - Thêm view type STREAMING cho text cập nhật dần theo từng delta.
 */
class AiMessageAdapter(
    private val markwon: Markwon,
    private val onAttachmentTap: (AttachmentRef) -> Unit,
    private val onReferenceTap: (AiReference) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onEdit: (AiChatItem.Message) -> Unit,
) : ListAdapter<AiChatItem, RecyclerView.ViewHolder>(DiffCb()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AiChatItem.Message   -> VIEW_TYPE_MESSAGE
        is AiChatItem.ToolCall  -> VIEW_TYPE_TOOL_CALL
        is AiChatItem.Thinking  -> VIEW_TYPE_THINKING
        is AiChatItem.Streaming -> VIEW_TYPE_STREAMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MESSAGE   -> MessageVH(
                ItemAiMessageBinding.inflate(inflater, parent, false),
                markwon,
                onAttachmentTap,
                onReferenceTap,
                onCopy,
                onEdit,
            )
            VIEW_TYPE_TOOL_CALL -> ToolCallVH(ItemAiToolCallBinding.inflate(inflater, parent, false))
            VIEW_TYPE_THINKING  -> ThinkingVH(inflater.inflate(R.layout.item_ai_thinking, parent, false))
            VIEW_TYPE_STREAMING -> StreamingVH(
                inflater.inflate(R.layout.item_ai_streaming, parent, false), markwon,
            )
            else                -> error("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AiChatItem.Message   -> (holder as MessageVH).bind(item)
            is AiChatItem.ToolCall  -> (holder as ToolCallVH).bind(item)
            is AiChatItem.Streaming -> (holder as StreamingVH).bind(item)
            is AiChatItem.Thinking  -> Unit
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is ThinkingVH) holder.startAnim()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is ThinkingVH) holder.stopAnim()
    }

    // ─────────────────────────────────────────────────────────────
    // Message bubble + attachment chips + reference chips + markdown
    // ─────────────────────────────────────────────────────────────
    class MessageVH(
        private val b: ItemAiMessageBinding,
        private val markwon: Markwon,
        private val onAttachmentTap: (AttachmentRef) -> Unit,
        private val onReferenceTap: (AiReference) -> Unit,
        private val onCopy: (String) -> Unit,
        private val onEdit: (AiChatItem.Message) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AiChatItem.Message) {
            val isUser = item.role == AiMessage.Role.USER
            b.userSide.isVisible      = isUser
            b.assistantSide.isVisible = !isUser

            if (isUser) {
                b.tvUser.text = item.content
                bindAttachments(b.userAttachmentsContainer, item.attachments)
                b.btnEdit.setOnClickListener { onEdit(item) }
                b.btnCopyUser.setOnClickListener { onCopy(item.content) }
            } else {
                bindAssistantText(item)
                bindAttachments(b.assistantAttachmentsContainer, item.attachments)
                b.btnCopy.setOnClickListener { onCopy(item.content) }
            }
        }

        /**
         * Render markdown reply. Nếu có reference → chèn chip bấm được lên trên
         * spanned markdown.
         *
         * QUAN TRỌNG: phải dùng [Markwon.setParsedMarkdown] chứ KHÔNG gán
         * `tv.text = ...`. setParsedMarkdown gọi afterSetText() — bước mà
         * TablePlugin cần để layout bảng; thiếu nó thì bảng bị đè chữ.
         */
        private fun bindAssistantText(item: AiChatItem.Message) {
            val rendered: Spanned = markwon.toMarkdown(item.content)
            val finalText: CharSequence =
                if (item.references.isEmpty()) rendered
                else applyReferences(rendered, item.references, onReferenceTap)
            val spanned = finalText as? Spanned ?: SpannableString(finalText)
            markwon.setParsedMarkdown(b.tvAssistant, spanned)
            b.tvAssistant.movementMethod = LinkMovementMethod.getInstance()
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
    // Streaming bubble — markdown cập nhật dần, có con trỏ ▌
    // ─────────────────────────────────────────────────────────────
    class StreamingVH(
        view: View,
        private val markwon: Markwon,
    ) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvStreaming)

        fun bind(item: AiChatItem.Streaming) {
            // "▌" báo đang gõ; nếu chưa có chữ nào hiện mỗi con trỏ.
            markwon.setMarkdown(tv, item.content + CURSOR)
        }

        companion object {
            private const val CURSOR = " ▌"
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
    // Thinking bubble — câu trạng thái xoay vòng, fade in/out
    // ─────────────────────────────────────────────────────────────
    class ThinkingVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvThinkingStatus)
        private val handler = Handler(Looper.getMainLooper())
        private var idx = 0

        private val cycleRunnable = object : Runnable {
            override fun run() {
                idx = (idx + 1) % MESSAGES.size
                tv.animate().alpha(0f).setDuration(250).withEndAction {
                    tv.text = MESSAGES[idx]
                    tv.animate().alpha(1f).setDuration(250).start()
                }.start()
                handler.postDelayed(this, INTERVAL_MS)
            }
        }

        fun startAnim() {
            idx = 0
            tv.text = MESSAGES[0]
            tv.alpha = 1f
            handler.postDelayed(cycleRunnable, INTERVAL_MS)
        }

        fun stopAnim() {
            handler.removeCallbacks(cycleRunnable)
            tv.animate().cancel()
            tv.alpha = 1f
        }

        companion object {
            private const val INTERVAL_MS = 2500L
            private val MESSAGES = listOf(
                "Đang suy nghĩ...",
                "Đang phân tích câu hỏi...",
                "Đang tìm kiếm thông tin...",
                "Đang xử lý dữ liệu...",
                "Sắp có câu trả lời rồi...",
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DiffUtil
    // ─────────────────────────────────────────────────────────────
    class DiffCb : DiffUtil.ItemCallback<AiChatItem>() {
        override fun areItemsTheSame(a: AiChatItem, b: AiChatItem) = when {
            // Chỉ tồn tại 1 Thinking / 1 Streaming tại 1 thời điểm → coi là "cùng
            // item" để cập nhật tại chỗ (mượt) thay vì remove+add (giật).
            a is AiChatItem.Thinking  && b is AiChatItem.Thinking  -> true
            a is AiChatItem.Streaming && b is AiChatItem.Streaming -> true
            else -> a === b
        }

        override fun areContentsTheSame(a: AiChatItem, b: AiChatItem) = a == b
    }

    companion object {
        private const val VIEW_TYPE_MESSAGE   = 1
        private const val VIEW_TYPE_TOOL_CALL = 2
        private const val VIEW_TYPE_THINKING  = 3
        private const val VIEW_TYPE_STREAMING = 4
        private const val FALLBACK_TAG_COLOR  = 0xFF607D8B.toInt()  // blue-grey
    }
}
