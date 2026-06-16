package com.project.hustassistant.ui.email

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.hustassistant.data.email.Email
import com.project.hustassistant.databinding.ItemEmailBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * EmailAdapter — bind Email (external model) thay cho NetworkEmail.
 *
 * THAY ĐỔI:
 *  - Type: NetworkEmail → Email.
 *  - Bỏ onDeleteClick (email read-only).
 *  - btnDelete trong layout giờ ẩn đi (xem item_email.xml mới).
 */
class EmailAdapter(
    private val onClick: (Email) -> Unit,
) : ListAdapter<Email, EmailAdapter.VH>(DiffCb()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEmailBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemEmailBinding) : RecyclerView.ViewHolder(b.root) {

        private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val displayFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        fun bind(email: Email) {
            // sender thường là "Tên <abc@xyz>" — chỉ hiện phần Tên cho gọn
            b.tvSender.text = email.sender
                ?.substringBefore('<')?.trim()
                ?.ifBlank { email.sender }
                ?: "(no sender)"
            b.tvSubject.text = email.subject ?: "(no subject)"
            b.tvSnippet.text = email.snippet ?: ""
            b.tvTime.text = parseTime(email.receivedAt)

            if (!email.accountEmail.isNullOrBlank()) {
                b.tvAccount.visibility = View.VISIBLE
                b.tvAccount.text = email.accountEmail
            } else {
                b.tvAccount.visibility = View.GONE
            }

            b.root.setOnClickListener { onClick(email) }
        }

        private fun parseTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try {
                val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
                displayFmt.format(isoFmt.parse(clean) ?: return "")
            } catch (_: Exception) {
                ""
            }
        }
    }

    class DiffCb : DiffUtil.ItemCallback<Email>() {
        override fun areItemsTheSame(a: Email, b: Email) = a.id == b.id
        override fun areContentsTheSame(a: Email, b: Email) = a == b
    }
}
