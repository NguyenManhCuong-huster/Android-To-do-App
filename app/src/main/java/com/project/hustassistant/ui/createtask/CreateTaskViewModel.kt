package com.project.hustassistant.ui.createtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.project.hustassistant.data.tag.TagRepository
import com.project.hustassistant.data.task.Priority
import com.project.hustassistant.data.task.TaskRepository
import com.project.hustassistant.data.tasktag.TaskTagRepository
import com.project.hustassistant.notification.TaskNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class CreateTaskViewModel(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val taskTagRepository: TaskTagRepository,
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {
    // --- STATE FLOWS (Lưu trạng thái UI) ---
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _location = MutableStateFlow("")
    val location: StateFlow<String> = _location.asStateFlow()

    private val _startTime = MutableStateFlow(0L)
    val startTime: StateFlow<Long> = _startTime.asStateFlow()

    private val _endTime = MutableStateFlow(0L)
    val endTime: StateFlow<Long> = _endTime.asStateFlow()

    private val _priority = MutableStateFlow(Priority.MEDIUM)
    val priority: StateFlow<Priority> = _priority.asStateFlow()

    // Quản lý Tags
    private val _currentTagIds = MutableStateFlow<Set<String>>(emptySet())
    val currentTagIds: StateFlow<Set<String>> = _currentTagIds.asStateFlow()

    // Lấy tất cả tags từ DB (Flow)
    val allTags = tagRepository.getTagsStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Kết hợp allTags và currentTagIds để tạo list hiển thị lên RecyclerView
    val selectedTagsList = combine(allTags, _currentTagIds) { tags, ids ->
        tags.filter { ids.contains(it.id) }
    }.asLiveData()

    // Trạng thái xử lý (Error / Success)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _taskCreated = MutableStateFlow(false)
    val taskCreated: StateFlow<Boolean> = _taskCreated.asStateFlow()

    // --- INIT ---
    init {
        // Khởi tạo thời gian mặc định ngay khi ViewModel được tạo ra
        // Logic này chỉ chạy 1 lần, xoay màn hình không bị reset
        val now = Calendar.getInstance()
        _startTime.value = now.timeInMillis
        // Mặc định kết thúc sau 1 tiếng
        now.add(Calendar.HOUR_OF_DAY, 1)
        _endTime.value = now.timeInMillis
    }

    // --- SETTERS (UI gọi cập nhật vào ViewModel) ---
    fun setTitle(value: String) {
        _title.value = value
    }

    fun setDescription(value: String) {
        _description.value = value
    }

    fun setLocation(value: String) {
        _location.value = value
    }

    fun setStartTime(time: Long) {
        _startTime.value = time
    }

    fun setEndTime(time: Long) {
        _endTime.value = time
    }

    fun setPriority(value: Priority) {
        _priority.value = value
    }

    // --- LOGIC TAGS ---
    fun toggleTagSelection(tagId: String, isSelected: Boolean) {
        val current = _currentTagIds.value.toMutableSet()
        if (isSelected) {
            current.add(tagId)
        } else {
            current.remove(tagId)
        }
        _currentTagIds.value = current
    }

    fun removeTag(tagId: String) {
        val current = _currentTagIds.value.toMutableSet()
        current.remove(tagId)
        _currentTagIds.value = current
    }

    // --- ACTION: CREATE TASK ---
    fun createTask() {
        // Lấy giá trị hiện tại từ StateFlow
        val titleVal = _title.value
        val descVal = _description.value
        val startVal = _startTime.value
        val endVal = _endTime.value
        val priorityVal = _priority.value
        val locVal = _location.value
        val tagIdsVal = _currentTagIds.value.toList()

        // Validation
        if (titleVal.isBlank()) {
            _errorMessage.value = "Title cannot be empty"
            return
        }
        if (descVal.isBlank()) {
            _errorMessage.value = "Description cannot be empty"
            return
        }
        if (startVal > endVal) {
            _errorMessage.value = "Start time must be before end time"
            return
        }

        viewModelScope.launch {
            // 1. Tạo Task và lấy ID
            val taskId = taskRepository.createTask(
                title = titleVal,
                description = descVal,
                start = startVal,
                end = endVal,
                priority = priorityVal,
                addressName = locVal
            )

            // 2. Lưu Tags
            if (tagIdsVal.isNotEmpty()) {
                taskTagRepository.updateTagsToTask(taskId, tagIdsVal)
            }

            // 3. Đặt lịch thông báo
            taskNotificationManager.scheduleTaskNotification(
                taskId = taskId,
                title = titleVal,
                message = descVal,
                timeInMillis = startVal
            )

            // 4. Báo thành công
            _taskCreated.value = true
        }
    }

    // Reset lỗi sau khi Toast
    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        fun provideFactory(
            taskRepository: TaskRepository,
            tagRepository: TagRepository,
            taskTagRepository: TaskTagRepository,
            taskNotificationManager: TaskNotificationManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreateTaskViewModel::class.java)) {
                    return CreateTaskViewModel(
                        taskRepository,
                        tagRepository,
                        taskTagRepository,
                        taskNotificationManager
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
