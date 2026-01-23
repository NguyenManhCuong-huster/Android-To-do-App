package com.project3.todoapp.ui.taskdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.tag.TagRepository
import com.project3.todoapp.data.task.Priority
import com.project3.todoapp.data.task.Task
import com.project3.todoapp.data.task.TaskRepository
import com.project3.todoapp.data.tasktag.TaskTagRepository
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,       // Thêm
    private val taskTagRepository: TaskTagRepository, // Thêm
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _taskUpdated = MutableLiveData<Boolean>()
    val taskUpdated: LiveData<Boolean> = _taskUpdated

    // Lấy danh sách tất cả Tag để hiển thị trong Dialog chọn
    val allTags = tagRepository.getTagsStream().asLiveData()

    // Hàm lấy chi tiết Task (Lưu ý: Task trả về cần bao gồm list Tags nếu repository đã support)
    suspend fun getTask(taskId: String): Task? {
        return taskRepository.getTask(taskId)
    }

    fun updateTask(
        id: String,
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority, // Thêm
        location: String,   // Thêm
        tagIds: List<String> // Thêm
    ) {
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            _taskUpdated.value = false
            return
        }
        viewModelScope.launch {
            // 1. Cập nhật thông tin cơ bản của Task
            taskRepository.updateTask(
                taskId = id,
                title = title,
                description = description,
                start = start,
                end = end,
                priority = priority,
                addressName = location
            )

            // 2. Cập nhật Tags (Xóa cũ, thêm mới - logic nằm trong repository)
            taskTagRepository.updateTagsToTask(id, tagIds)

            // 3. Cập nhật thông báo
            taskNotificationManager.scheduleTaskNotification(
                taskId = id,
                title = title,
                message = description,
                timeInMillis = start
            )

            _taskUpdated.value = true
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