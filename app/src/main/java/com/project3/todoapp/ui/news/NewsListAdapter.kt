package com.project3.todoapp.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.news.News
import com.project3.todoapp.data.news.NewsKind
import com.project3.todoapp.databinding.ItemNewsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NewsListAdapter — render news/plan card.
 *
 * 1 layout duy nhất [item_news.xml] dùng cho cả NEWS và PLAN; khác nhau ở việc
 * có hiển thị thumbnail hay không (PLAN không có).
 *
 * Click → [onClick] (NewsListActivity gắn vào để mở detail).
 */
class NewsListAdapter(
    private val onClick: (News) -> Unit,
) : ListAdapter<News, NewsListAdapter.VH>(DiffCb()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemNewsBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: News) {
            b.tvTitle.text = item.title

            // Kind badge: NEWS = "Tin tức", PLAN = "Kế hoạch"
            b.tvKind.text = when (item.kind) {
                NewsKind.NEWS -> "Tin tức"
                NewsKind.PLAN -> "Kế hoạch"
            }
            b.tvKind.setBackgroundResource(
                when (item.kind) {
                    NewsKind.NEWS -> com.project3.todoapp.R.drawable.bg_news_kind_chip_news
                    NewsKind.PLAN -> com.project3.todoapp.R.drawable.bg_news_kind_chip_plan
                }
            )

            // Tag (CTSV/ĐTĐH/...): chỉ hiện khi có
            b.tvTag.isVisible = !item.tag.isNullOrBlank()
            b.tvTag.text      = item.tag.orEmpty()

            // Ngày publish
            b.tvDate.isVisible = item.publishedAt > 0L
            if (item.publishedAt > 0L) {
                b.tvDate.text = DATE_FMT.format(Date(item.publishedAt))
            }

            // Summary preview — 2 dòng đầu
            b.tvSummary.isVisible = !item.summary.isNullOrBlank()
            b.tvSummary.text      = item.summary.orEmpty()

            b.root.setOnClickListener { onClick(item) }
        }
    }

    private class DiffCb : DiffUtil.ItemCallback<News>() {
        override fun areItemsTheSame(a: News, b: News)    = a.id == b.id
        override fun areContentsTheSame(a: News, b: News) = a == b
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))
    }
}
