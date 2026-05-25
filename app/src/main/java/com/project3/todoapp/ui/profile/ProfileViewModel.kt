package com.project3.todoapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.userinfo.UserInfo
import com.project3.todoapp.data.userinfo.UserInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ProfileViewModel — chỉ phụ thuộc UserInfoRepository.
 *
 * KHÁC BẢN CŨ:
 *  - Không gọi authApi / userInfoApi trực tiếp nữa.
 *  - userInfo đẩy ra StateFlow riêng (observe Flow từ Room) → UI tự update
 *    khi save xong (qua local) hoặc sync ghi xuống.
 *  - save() gọi repository.save() → ghi local trước (instant), repository
 *    tự push background. UI không cần đợi.
 *  - load() đổi tên thành sync() — pull từ server. Pull-to-refresh dùng cái này.
 *  - Bỏ field `user: UserDto?` trong UiState — email giờ nằm trong UserInfo.
 */
class ProfileViewModel(
    private val repository: UserInfoRepository,
) : ViewModel() {

    /** UI state — chỉ chứa UI flags, KHÔNG chứa data. Data đi qua [userInfo]. */
    data class UiState(
        val isSyncing: Boolean = false,
        val isSaving: Boolean = false,
        val isEditing: Boolean = false,
        val errorMessage: String? = null,
        val toastMessage: String? = null,
    )

    /** Profile data — observe trực tiếp từ Room qua repository. */
    val userInfo: StateFlow<UserInfo?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Lần đầu vào màn — pull từ server. Nếu offline cũng OK, vẫn có cache local.
        sync()
    }

    // ─── Edit mode ────────────────────────────────────────
    fun startEdit()  { _state.value = _state.value.copy(isEditing = true) }
    fun cancelEdit() { _state.value = _state.value.copy(isEditing = false) }

    // ─── Save (offline-friendly) ──────────────────────────
    /**
     * Ghi vào local ngay → UI cập nhật tức thì qua Flow.
     * Repository tự push background nếu online.
     */
    fun save(
        studentId: String?,
        fullName: String?,
        dateOfBirth: String?,
        phone: String?,
        school: String?,
        major: String?,
        className: String?,
        course: String?,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            runCatching {
                repository.save(
                    studentId, fullName, dateOfBirth, phone,
                    school, major, className, course,
                )
            }.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        isEditing = false,
                        toastMessage = "Đã lưu thông tin",
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Lưu thất bại",
                    )
                },
            )
        }
    }

    // ─── Sync (pull) ──────────────────────────────────────
    fun sync() {
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

    fun clearToast() { _state.value = _state.value.copy(toastMessage = null) }
    fun clearError() { _state.value = _state.value.copy(errorMessage = null) }

    companion object {
        fun provideFactory(repository: UserInfoRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(cls: Class<T>): T =
                    ProfileViewModel(repository) as T
            }
    }
}
