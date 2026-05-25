package com.project3.todoapp.ui.news

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.news.News
import com.project3.todoapp.data.news.NewsKind
import com.project3.todoapp.databinding.ActivityNewsDetailBinding
import com.project3.todoapp.ui.aichat.AiChatActivity
import com.project3.todoapp.ui.common.AttachmentAdapter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NewsDetailActivity — chi tiết 1 news.
 *
 * THAY ĐỔI 2026-05-23:
 *   - Hiển thị section "Tệp đính kèm" (rvAttachments) BÊN DƯỚI summary.
 *     Hidden khi list rỗng.
 *   - Tap chip → ViewModel.openAttachment → trigger download + Intent.ACTION_VIEW.
 *   - openEvents flow để tách concern giữa "download" (VM) và "fire intent" (Activity).
 */
class NewsDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsDetailBinding
    private lateinit var attachmentAdapter: AttachmentAdapter

    private val newsId: String by lazy { intent.getStringExtra(EXTRA_NEWS_ID).orEmpty() }

    private val attachmentRepo by lazy {
        (application as TodoApplication).container.attachmentRepository
    }

    private val viewModel: NewsDetailViewModel by viewModels {
        NewsDetailViewModel.provideFactory(
            newsId,
            (application as TodoApplication).container.newsRepository,
            attachmentRepo,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (newsId.isBlank()) {
            Toast.makeText(this, "Thiếu news_id", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        setupAttachments()
        observeState()
    }

    private fun setupAttachments() {
        attachmentAdapter = AttachmentAdapter(
            loadingIds = { viewModel.loadingIds.value },
            onTap      = { viewModel.openAttachment(it) },
        )
        binding.rvAttachments.adapter = attachmentAdapter
        binding.rvAttachments.layoutManager = LinearLayoutManager(this)
        binding.rvAttachments.isNestedScrollingEnabled = false
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.news.collect { news ->
                        if (news == null) {
                            binding.contentGroup.isVisible = false
                            binding.tvEmpty.isVisible = true
                        } else {
                            binding.tvEmpty.isVisible = false
                            binding.contentGroup.isVisible = true
                            bind(news)
                        }
                    }
                }
                launch {
                    viewModel.attachments.collect { list ->
                        val show = list.isNotEmpty()
                        binding.tvAttachmentsHeader.isVisible = show
                        binding.rvAttachments.isVisible = show
                        attachmentAdapter.submitList(list)
                    }
                }
                launch {
                    viewModel.loadingIds.collect {
                        // Rebind toàn bộ để loading state visualize. List có max ~5 chip
                        // nên rebind toàn bộ rẻ.
                        attachmentAdapter.notifyDataSetChanged()
                    }
                }
                launch {
                    viewModel.openEvents.collect { event ->
                        if (event != null) {
                            handleOpenEvent(event)
                            viewModel.consumeOpenEvent()
                        }
                    }
                }
            }
        }
    }

    private fun handleOpenEvent(event: NewsDetailViewModel.OpenEvent) {
        when (event) {
            is NewsDetailViewModel.OpenEvent.Ready -> {
                val intent = attachmentRepo.buildOpenIntent(File(event.absolutePath), event.mimeType)
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "Không có app để mở file này", Toast.LENGTH_LONG).show()
                }
            }
            is NewsDetailViewModel.OpenEvent.Error ->
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun bind(news: News) {
        binding.tvKind.text = when (news.kind) {
            NewsKind.NEWS -> "Tin tức"
            NewsKind.PLAN -> "Kế hoạch"
        }
        binding.tvKind.setBackgroundResource(
            when (news.kind) {
                NewsKind.NEWS -> com.project3.todoapp.R.drawable.bg_news_kind_chip_news
                NewsKind.PLAN -> com.project3.todoapp.R.drawable.bg_news_kind_chip_plan
            }
        )

        binding.tvTag.isVisible = !news.tag.isNullOrBlank()
        binding.tvTag.text      = news.tag.orEmpty()

        binding.tvDate.isVisible = news.publishedAt > 0L
        if (news.publishedAt > 0L) {
            binding.tvDate.text = DATE_FMT.format(Date(news.publishedAt))
        }

        binding.tvTitle.text = news.title
        binding.tvSummary.text = news.summary?.takeIf { it.isNotBlank() }
            ?: "(Không có nội dung)"

        val hasUrl = !news.articleUrl.isNullOrBlank()
        binding.btnOpenSource.isEnabled = hasUrl
        binding.btnOpenSource.setOnClickListener {
            if (hasUrl) openInBrowser(news.articleUrl!!)
        }

        binding.fabAiChat.setOnClickListener {
            AiChatActivity.startNews(this, news.id)
        }
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được link", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_NEWS_ID = "news_id"
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))
    }
}
