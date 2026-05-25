package com.project3.todoapp.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.R
import com.project3.todoapp.data.attachment.Attachment
import com.project3.todoapp.databinding.ItemAttachmentBinding
import java.util.Locale

/**
 * AttachmentAdapter — dùng chung cho NewsDetailActivity và ThreadMessageAdapter.
 *
 * Mỗi item là 1 "chip" hiển thị file:
 *   ┌────────────────────────────────────────┐
 *   │ [icon]  proposal.pdf                   │
 *   │         PDF · 1.2 MB                   │
 *   └────────────────────────────────────────┘
 *
 * State:
 *   • Normal — tap → callback (Activity sẽ download + open).
 *   • Loading — đang download, disable tap, hiện progress.
 *   • NotAvailable — file quá lớn hoặc download fail ở server,
 *     opacity giảm + label đỏ.
 *
 * Loading state quản lý ngoài (Activity giữ Set<attachmentId>) và truyền vào
 * qua [submitList] kèm payload — hoặc đơn giản hơn: rebind toàn bộ. Để giữ
 * simple, chỉ giữ state qua `loadingIds` truyền lúc construct (mutable trỏ ngoài).
 */
class AttachmentAdapter(
    private val loadingIds: () -> Set<String>,
    private val onTap: (Attachment) -> Unit,
) : ListAdapter<Attachment, AttachmentAdapter.VH>(DiffCb()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), loadingIds())
    }

    inner class VH(private val b: ItemAttachmentBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: Attachment, loading: Set<String>) {
            b.tvName.text = item.fileName

            // Subtitle: TYPE · SIZE — vd "PDF · 1.2 MB"
            val ext = item.fileName.substringAfterLast('.', "").uppercase(Locale.US)
                .takeIf { it.isNotBlank() } ?: item.mimeType?.substringAfterLast('/')?.uppercase()
                .orEmpty()
            val sizeStr = formatSize(item.sizeBytes)
            b.tvMeta.text = listOf(ext, sizeStr).filter { it.isNotBlank() }.joinToString(" · ")

            val isLoading = item.id in loading

            // Mỗi trạng thái có style riêng:
            //   • loading → progress, alpha 0.7
            //   • !isDownloaded ở server → alpha 0.5, label "Không có sẵn", click vẫn được
            //     (sẽ show Toast)
            //   • OK → alpha 1.0
            when {
                isLoading -> {
                    b.progress.visibility = View.VISIBLE
                    b.icon.visibility = View.INVISIBLE
                    b.root.alpha = 0.7f
                    b.root.isEnabled = false
                }
                !item.isDownloaded -> {
                    b.progress.visibility = View.GONE
                    b.icon.visibility = View.VISIBLE
                    b.icon.setImageResource(R.drawable.ic_attach_file)
                    b.root.alpha = 0.5f
                    b.root.isEnabled = true
                }
                else -> {
                    b.progress.visibility = View.GONE
                    b.icon.visibility = View.VISIBLE
                    b.icon.setImageResource(R.drawable.ic_attach_file)
                    b.root.alpha = 1.0f
                    b.root.isEnabled = true
                }
            }

            b.root.setOnClickListener {
                if (!isLoading) onTap(item)
            }
        }
    }

    private fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else      -> "$bytes B"
        }
    }

    class DiffCb : DiffUtil.ItemCallback<Attachment>() {
        override fun areItemsTheSame(a: Attachment, b: Attachment) = a.id == b.id
        override fun areContentsTheSame(a: Attachment, b: Attachment) = a == b
    }
}
