package com.project3.todoapp.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.news.News
import com.project3.todoapp.data.news.NewsKind
import com.project3.todoapp.data.news.NewsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * NewsListViewModel — state cho [NewsListActivity].
 *
 * Filter qua tab: ALL / NEWS_ONLY / PLAN_ONLY / RECOMMENDED (← MỚI).
 * Khi đổi tab, [filter] update → [news] tự switch sang Flow tương ứng từ
 * repository (không re-subscribe DAO).
 *
 * Pull-to-refresh: [refresh] gọi đúng method tuỳ tab đang active —
 *  RECOMMENDED → refreshRecommendations(), còn lại → refresh() thường.
 *
 * Lỗi nuốt im lặng — UI chỉ tắt swipe spinner.
 */
class NewsListViewModel(
    private val repository: NewsRepository,
) : ViewModel() {

    enum class Filter { ALL, NEWS_ONLY, PLAN_ONLY, RECOMMENDED }

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val news: StateFlow<List<News>> = _filter.flatMapLatest { f ->
        when (f) {
            Filter.ALL         -> repository.getNewsStream()
            Filter.NEWS_ONLY   -> repository.getNewsStream(kind = NewsKind.NEWS)
            Filter.PLAN_ONLY   -> repository.getNewsStream(kind = NewsKind.PLAN)
            Filter.RECOMMENDED -> repository.getRecommendationsStream()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: Filter) { _filter.value = f }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                if (_filter.value == Filter.RECOMMENDED) {
                    repository.refreshRecommendations()
                } else {
                    repository.refresh()
                }
            } catch (_: Exception) {
                // Swallow — repository đã log; UI chỉ cần tắt spinner.
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Dismiss 1 đề xuất (gọi từ swipe / nút X trên card). */
    fun dismissRecommendation(newsId: String) {
        viewModelScope.launch {
            try { repository.dismissRecommendation(newsId) } catch (_: Exception) {}
        }
    }

    companion object {
        fun provideFactory(repository: NewsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NewsListViewModel(repository) as T
            }
    }
}
