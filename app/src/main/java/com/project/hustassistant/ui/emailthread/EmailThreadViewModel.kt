package com.project.hustassistant.ui.emailthread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.hustassistant.data.attachment.Attachment
import com.project.hustassistant.data.attachment.AttachmentRepository
import com.project.hustassistant.data.email.EmailRepository
import com.project.hustassistant.data.email.ThreadMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * EmailThreadViewModel — load thread + attachments cho TỪNG message trong thread.
 *
 * THAY ĐỔI 2026-05-23:
 *  - [attachmentsByMessageId]: Map<gmailMessageId, List<Attachment>> — gọi 1
 *    lần sau khi load thread, KHÔNG observe Flow per-message (tránh n Flow
 *    cho n message).
 *  - [openAttachment] same flow như NewsDetailViewModel.
 *
 * Hạn chế đã chấp nhận: nếu attachments của 1 email update giữa lúc đang xem
 * thread → không tự refresh, user phải đóng/mở lại. Hợp lý vì người đọc thread
 * hiếm khi cần sync giữa session.
 */
class EmailThreadViewModel(
    private val emailId: String,
    private val repository: EmailRepository,
    private val attachmentRepository: AttachmentRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val messages: List<ThreadMessage> = emptyList(),
        // gmailMessageId → list attachments
        val attachmentsByMessageId: Map<String, List<Attachment>> = emptyMap(),
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _loadingIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingIds: StateFlow<Set<String>> = _loadingIds.asStateFlow()

    private val _openEvents = MutableStateFlow<OpenEvent?>(null)
    val openEvents: StateFlow<OpenEvent?> = _openEvents.asStateFlow()

    init { loadThread() }

    fun loadThread() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            repository.getThread(emailId).fold(
                onSuccess = { messages ->
                    val attMap = loadAttachmentsForMessages(messages)
                    _state.value = UiState(
                        isLoading = false,
                        messages  = messages,
                        attachmentsByMessageId = attMap,
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Lỗi tải thread",
                    )
                },
            )
        }
    }

    /**
     * Với mỗi message trong thread, query Room theo emailId (lookup nội bộ).
     *
     * Note: ThreadMessage hiện chỉ có gmailMessageId, không có emailId. Cần
     * map gmailMessageId → emailId qua repository. Để tránh hit DB n lần,
     * 1 query duy nhất ko khả thi với hiện tại; chấp nhận n suspend call
     * (cùng dispatcher IO, batch nhỏ ~5-10 msg).
     */
    private suspend fun loadAttachmentsForMessages(
        messages: List<ThreadMessage>,
    ): Map<String, List<Attachment>> {
        val out = mutableMapOf<String, List<Attachment>>()
        for (msg in messages) {
            // ThreadMessage chỉ có gmailMessageId. Cần email row trong Room
            // để lấy id (UUID). Repository.getThread đã lấy từ Room → có
            // gmailMessageId. Ở đây ta query attachment bằng EMAIL ownerId.
            // ownerId của attachment = email row UUID, KHÔNG phải gmailMessageId.
            //
            // Solution: query qua repository.getEmailByGmailMessageId (cần helper).
            // Để giữ change ít, dùng cách khác: gọi observeForEmail(emailId)
            // nhưng emailId = ??? ↓
            //
            // Workaround đơn giản nhất: với mỗi message, lookup email UUID qua
            // EmailRepository.getEmail(gmailMessageId). NHƯNG repository hiện
            // tại không có method theo gmailMessageId. Thêm cũng được, nhưng
            // tránh modify thêm: ta dùng anchor email (cái user click) làm root,
            // và iterate qua attachmentRepository.getForEmail cho TỪNG email
            // trong thread bằng email UUID đã có ở LocalEmail.id (sẵn trong DAO).
            //
            // Pragmatic: query DAO bằng email UUID = ??? — bí. Solution xài
            // được luôn: lưu kèm emailId trong ThreadMessage.
            //
            // ↓ ↓ ↓ Để fix đúng tách layer: ta cần thêm field `id` (UUID local
            // email) vào ThreadMessage. Xem ModelMappingExt.kt được cập nhật.
            val attachments = attachmentRepository.getForEmail(msg.localEmailId)
            out[msg.gmailMessageId] = attachments
        }
        return out
    }

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
                        _openEvents.value = OpenEvent.Error("File chưa sẵn sàng trên server.")
                }
            } finally {
                _loadingIds.value = _loadingIds.value - attachment.id
            }
        }
    }

    fun consumeOpenEvent() { _openEvents.value = null }

    fun clearError() { _state.value = _state.value.copy(errorMessage = null) }

    sealed class OpenEvent {
        data class Ready(val absolutePath: String, val mimeType: String?, val attachmentId: String) : OpenEvent()
        data class Error(val message: String) : OpenEvent()
    }

    companion object {
        fun provideFactory(
            emailId: String,
            repository: EmailRepository,
            attachmentRepository: AttachmentRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(cls: Class<T>): T =
                EmailThreadViewModel(emailId, repository, attachmentRepository) as T
        }
    }
}
