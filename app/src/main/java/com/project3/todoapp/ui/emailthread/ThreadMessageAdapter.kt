package com.project3.todoapp.ui.emailthread

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.attachment.Attachment
import com.project3.todoapp.data.email.ThreadMessage
import com.project3.todoapp.databinding.ItemThreadMessageBinding
import com.project3.todoapp.ui.common.AttachmentAdapter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ThreadMessageAdapter — render thread messages + attachments per message.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Constructor nhận thêm 3 callback: attachmentsFor (lookup per-message),
 *    loadingIds (state share toàn adapter), onAttachmentTap.
 *  - Mỗi item card chứa 1 RecyclerView nested cho attachments. RV này
 *    isNestedScrollingEnabled=false để outer RV (rvThread) scroll mượt.
 *
 * Behavior cũ giữ nguyên:
 *  - Message cuối cùng (= mail user click) → expand full body, không có nút.
 *  - Message khác → collapse 3 dòng + nút "Hiển thị thêm".
 */
class ThreadMessageAdapter(
    private val attachmentsFor: (gmailMessageId: String) -> List<Attachment>,
    private val loadingIds: () -> Set<String>,
    private val onAttachmentTap: (Attachment) -> Unit,
) : ListAdapter<ThreadMessage, ThreadMessageAdapter.VH>(DiffCb()) {

    private val expandedIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemThreadMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val isLast = position == itemCount - 1
        holder.bind(getItem(position), isLast)
    }

    inner class VH(private val b: ItemThreadMessageBinding) : RecyclerView.ViewHolder(b.root) {

        // 1 adapter per ViewHolder để giữ DiffUtil state khi rebind.
        private val attachmentAdapter = AttachmentAdapter(
            loadingIds = loadingIds,
            onTap      = onAttachmentTap,
        )

        init {
            b.rvAttachments.layoutManager = LinearLayoutManager(b.root.context)
            b.rvAttachments.adapter = attachmentAdapter
            b.rvAttachments.isNestedScrollingEnabled = false
        }

        private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val displayFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(msg: ThreadMessage, isCurrent: Boolean) {
            b.tvFrom.text = msg.sender ?: "(unknown)"

            if (!msg.recipient.isNullOrBlank()) {
                b.tvTo.visibility = View.VISIBLE
                b.tvTo.text = "đến: ${msg.recipient}"
            } else {
                b.tvTo.visibility = View.GONE
            }

            b.tvDate.text = formatDate(msg.receivedAt)
            b.tvSubject.text = msg.subject ?: "(no subject)"
            b.tvBody.text = pickBody(msg)

            if (isCurrent) {
                b.tvBody.maxLines = Int.MAX_VALUE
                b.btnExpand.visibility = View.GONE
            } else {
                val expanded = msg.gmailMessageId in expandedIds
                b.tvBody.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES
                b.btnExpand.visibility = View.VISIBLE
                b.btnExpand.text = if (expanded) "Thu gọn" else "Hiển thị thêm"
                b.btnExpand.setOnClickListener {
                    if (msg.gmailMessageId in expandedIds) expandedIds.remove(msg.gmailMessageId)
                    else expandedIds.add(msg.gmailMessageId)
                    notifyItemChanged(bindingAdapterPosition)
                }
            }

            // ── Attachments ──
            val files = attachmentsFor(msg.gmailMessageId)
            if (files.isEmpty()) {
                b.attachmentsGroup.visibility = View.GONE
            } else {
                b.attachmentsGroup.visibility = View.VISIBLE
                b.tvAttachmentsLabel.text =
                    if (files.size == 1) "1 tệp đính kèm" else "${files.size} tệp đính kèm"
                attachmentAdapter.submitList(files)
            }
        }

        private fun pickBody(msg: ThreadMessage): CharSequence {
            if (msg.bodyText.isNotBlank()) return msg.bodyText
            if (msg.bodyHtml.isNotBlank()) {
                @Suppress("DEPRECATION")
                return Html.fromHtml(msg.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            }
            return msg.snippet ?: ""
        }

        private fun formatDate(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try {
                val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
                displayFmt.format(isoFmt.parse(clean) ?: return "")
            } catch (_: Exception) {
                ""
            }
        }
    }

    class DiffCb : DiffUtil.ItemCallback<ThreadMessage>() {
        override fun areItemsTheSame(a: ThreadMessage, b: ThreadMessage) =
            a.gmailMessageId == b.gmailMessageId

        override fun areContentsTheSame(a: ThreadMessage, b: ThreadMessage) = a == b
    }

    companion object {
        private const val COLLAPSED_LINES = 3
    }
}
