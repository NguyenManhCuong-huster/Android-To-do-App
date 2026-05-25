package com.project3.todoapp.ui.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.email.Email
import com.project3.todoapp.data.email.EmailRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * EmailViewModel — local-first, read-only.
 *
 * THAY ĐỔI (2025-05-02):
 *  - refresh() gọi repository.sync() không truyền query. Sync luôn full.
 *  - Search query CHỈ ảnh hưởng đến filter Room qua getEmailsStream(q),
 *    không trigger sync.
 */
class EmailViewModel(
    private val repository: EmailRepository,
) : ViewModel() {

    data class UiState(
        val isSyncing: Boolean = false,
        val isPullingFromGmail: Boolean = false,
        val errorMessage: String? = null,
        val toastMessage: String? = null,
    )

    private val _query = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val emails: StateFlow<List<Email>> = _query
        .flatMapLatest { repository.getEmailsStream(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun search(q: String) {
        _query.value = q.trim().ifBlank { null }
    }

    /** Pull TOÀN BỘ email từ server → local (mirror đầy đủ). */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, errorMessage = null)
            repository.sync().fold(
                onSuccess = {
                    _state.value = _state.value.copy(isSyncing = false)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        errorMessage = err.message,
                    )
                },
            )
        }
    }

    fun syncFromGmail() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPullingFromGmail = true, errorMessage = null)
            repository.syncFromGmail().fold(
                onSuccess = { summary ->
                    val total = summary.accounts?.sumOf { it.new_emails ?: 0 } ?: 0
                    _state.value = _state.value.copy(
                        isPullingFromGmail = false,
                        toastMessage = "Đã tải $total email mới từ Gmail",
                    )
                    refresh()
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isPullingFromGmail = false,
                        errorMessage = err.message,
                    )
                },
            )
        }
    }

    fun clearToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        fun provideFactory(repository: EmailRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(cls: Class<T>): T =
                    EmailViewModel(repository) as T
            }
    }
}