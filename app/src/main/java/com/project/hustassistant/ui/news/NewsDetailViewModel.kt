package com.project.hustassistant.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.hustassistant.data.attachment.Attachment
import com.project.hustassistant.data.attachment.AttachmentRepository
import com.project.hustassistant.data.news.News
import com.project.hustassistant.data.news.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * NewsDetailViewModel — load 1 news + observe attachments cho news đó.
 *
 * THAY ĐỔI 2026-05-23:
 *  - [attachments] stream từ AttachmentRepository.observeForNews — auto-update
 *    khi sync news refresh metadata.
 *  - [loadingIds] track attachment đang download, expose ra UI để adapter
 *    render spinner.
 */
class NewsDetailViewModel(
    private val newsId: String,
    private val repository: NewsRepository,
    private val attachmentRepository: AttachmentRepository,
) : ViewModel() {

    private val _news = MutableStateFlow<News?>(null)
    val news: StateFlow<News?> = _news.asStateFlow()

    val attachments: StateFlow<List<Attachment>> = attachmentRepository
        .observeForNews(newsId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loadingIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingIds: StateFlow<Set<String>> = _loadingIds.asStateFlow()

    private val _openEvents = MutableStateFlow<OpenEvent?>(null)
    val openEvents: StateFlow<OpenEvent?> = _openEvents.asStateFlow()

    init {
        load()
        // Best-effort fetch attachments metadata (nếu chưa có ở local từ trước).
        viewModelScope.launch {
            attachmentRepository.syncFromServer("NEWS", newsId)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _news.value = repository.getNews(newsId)
        }
    }

    /**
     * Triggered khi user tap chip. Download (nếu cần) rồi emit OpenEvent.
     * Activity collect → fire Intent.ACTION_VIEW.
     */
    fun openAttachment(attachment: Attachment) {
        if (attachment.id in _loadingIds.value) return
        _loadingIds.value = _loadingIds.value + attachment.id
        viewModelScope.launch {
            try {
                when (val r = attachmentRepository.downloadIfNeeded(attachment.id)) {
                    is AttachmentRepository.DownloadResult.Ready ->
                        _openEvents.value = OpenEvent.Ready(r.file.absolutePath, r.mimeType, attachment.id)
                    is AttachmentRepository.DownloadResult.Error ->
                        _openEvents.value = OpenEvent.Error(r.message)
                    AttachmentRepository.DownloadResult.NotAvailable ->
                        _openEvents.value = OpenEvent.Error("File chưa sẵn sàng trên server (quá lớn hoặc tải lỗi).")
                }
            } finally {
                _loadingIds.value = _loadingIds.value - attachment.id
            }
        }
    }

    fun consumeOpenEvent() { _openEvents.value = null }

    sealed class OpenEvent {
        data class Ready(val absolutePath: String, val mimeType: String?, val attachmentId: String) : OpenEvent()
        data class Error(val message: String) : OpenEvent()
    }

    companion object {
        fun provideFactory(
            newsId: String,
            repository: NewsRepository,
            attachmentRepository: AttachmentRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NewsDetailViewModel(newsId, repository, attachmentRepository) as T
        }
    }
}
