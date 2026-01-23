package com.project3.todoapp.ui.createtask

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.tag.TagRepository
import com.project3.todoapp.data.task.Priority
import com.project3.todoapp.data.task.TaskRepository
import com.project3.todoapp.data.tasktag.TaskTagRepository
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.launch

class CreateTaskViewModel(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val taskTagRepository: TaskTagRepository,
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    private val _taskCreated = MutableLiveData<Boolean>()
    val taskCreated: LiveData<Boolean> = _taskCreated

    // Lấy danh sách tất cả Tag để hiển thị lên Dialog chọn
    val allTags = tagRepository.getTagsStream().asLiveData()

    // Hàm create task giờ nhận thêm list tagIds và location
    fun createTask(
        title: String,
        description: String,
        start: Long,
        end: Long,
        tagIds: List<String> = emptyList(),
        location: String? = null,
        priority: Priority
    ) {
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            _taskCreated.value = false
            return
        }
        viewModelScope.launch {
            // 1. Tạo Task và lấy về TaskId
            val taskId = taskRepository.createTask(
                title = title,
                description = description,
                start = start,
                end = end,
                priority = priority, // Dùng biến vừa truyền vào
                addressName = location
            )

            // 2. Lưu danh sách Tag đã chọn vào bảng trung gian
            if (tagIds.isNotEmpty()) {
                taskTagRepository.updateTagsToTask(taskId, tagIds)
            }

            // 3. Đặt lịch thông báo
            taskNotificationManager.scheduleTaskNotification(
                taskId = taskId,
                title = title,
                message = description,
                timeInMillis = start
            )

            _taskCreated.value = true
        }
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