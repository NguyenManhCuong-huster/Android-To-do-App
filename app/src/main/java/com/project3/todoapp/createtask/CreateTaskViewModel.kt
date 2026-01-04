package com.project3.todoapp.createtask

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.TaskRepository
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.launch

class CreateTaskViewModel(
    private val repository: TaskRepository,
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _taskCreated = MutableLiveData<Boolean>()
    val taskCreated: LiveData<Boolean> = _taskCreated

    fun createTask(title: String, description: String, start: Long, end: Long) {
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            _taskCreated.value = false
            return
        }
        viewModelScope.launch {
            val taskId = repository.createTask(title, description, start, end)
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
            repository: TaskRepository,
            taskNotificationManager: TaskNotificationManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreateTaskViewModel::class.java)) {
                    return CreateTaskViewModel(repository, taskNotificationManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}