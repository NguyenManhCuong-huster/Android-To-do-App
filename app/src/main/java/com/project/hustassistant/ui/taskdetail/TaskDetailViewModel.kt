package com.project.hustassistant.ui.taskdetail

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

class TaskDetailViewModel(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val taskTagRepository: TaskTagRepository,
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {
    // --- STATE FLOWS (Lưu trạng thái của Task để không mất khi xoay màn hình) ---
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

    // Lấy tất cả tags từ DB
    val allTags = tagRepository.getTagsStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Kết hợp allTags và currentTagIds để tạo ra list Tag object hiển thị lên UI
    val selectedTagsList = combine(allTags, _currentTagIds) { tags, ids ->
        tags.filter { ids.contains(it.id) }
    }.asLiveData() // Dùng asLiveData để dễ observe trong Activity cũ, hoặc dùng collect trong coroutine

    // Trạng thái UI
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _taskUpdated = MutableStateFlow(false)
    val taskUpdated: StateFlow<Boolean> = _taskUpdated.asStateFlow()

    // Cờ đánh dấu đã load dữ liệu ban đầu
    private var isDataLoaded = false
    private var currentTaskId: String? = null

    // --- HÀM LOAD DỮ LIỆU ---
    fun loadTask(taskId: String) {
        // Nếu đã load rồi (do xoay màn hình ViewModel còn sống) thì KHÔNG load lại từ DB
        // Để giữ lại các thay đổi chưa lưu của người dùng.
        if (isDataLoaded && currentTaskId == taskId) return

        currentTaskId = taskId
        viewModelScope.launch {
            val task = taskRepository.getTask(taskId)
            if (task != null) {
                _title.value = task.title
                _description.value = task.description
                _location.value = task.addressName ?: ""
                _startTime.value = task.start
                _endTime.value = task.end
                _priority.value = task.priority
                _currentTagIds.value = task.tags.map { it.id }.toSet()

                isDataLoaded = true
            } else {
                _errorMessage.value = "Task not found"
            }
        }
    }

    // --- CÁC HÀM CẬP NHẬT TRẠNG THÁI TỪ UI ---
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

    // --- HÀM LƯU (SAVE) ---
    fun saveTask() {
        val taskId = currentTaskId ?: return
        val start = _startTime.value
        val end = _endTime.value
        val titleVal = _title.value
        val descVal = _description.value

        // Validate
        if (titleVal.isBlank()) {
            _errorMessage.value = "Title cannot be empty"
            return
        }
        if (descVal.isBlank()) {
            _errorMessage.value = "Description cannot be empty"
            return
        }
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            return
        }
        if (start == 0L || end == 0L) {
            _errorMessage.value = "Please select valid time"
            return
        }

        val finalTagIds = _currentTagIds.value.toList()

        viewModelScope.launch {
            // 1. Cập nhật thông tin task
            taskRepository.updateTask(
                taskId = taskId,
                title = titleVal,
                description = descVal,
                start = start,
                end = end,
                priority = _priority.value,
                addressName = _location.value
            )

            // 2. Cập nhật Tags
            taskTagRepository.updateTagsToTask(taskId, finalTagIds)

            // 3. Cập nhật thông báo
            taskNotificationManager.scheduleTaskNotification(
                taskId = taskId,
                title = titleVal,
                message = descVal,
                timeInMillis = start
            )

            _taskUpdated.value = true
        }
    }

    // Reset error message sau khi đã hiển thị Toast
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
                if (modelClass.isAssignableFrom(TaskDetailViewModel::class.java)) {
                    return TaskDetailViewModel(
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