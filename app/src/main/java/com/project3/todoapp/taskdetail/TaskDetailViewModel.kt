package com.project3.todoapp.taskdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.Task
import com.project3.todoapp.data.TaskRepository
import com.project3.todoapp.notification.NotificationUtils
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    private val repository: TaskRepository,
    application: Application
) : AndroidViewModel(application) {
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _taskUpdated = MutableLiveData<Boolean>()
    val taskUpdated: LiveData<Boolean> = _taskUpdated

    suspend fun getTask(taskId: String): Task? {
        return repository.getTask(taskId)
    }

    fun updateTask(id: String, title: String, description: String, start: Long, end: Long) {
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            _taskUpdated.value = false
            return
        }
        viewModelScope.launch {
            repository.updateTask(id, title, description, start, end)
            val context = getApplication<Application>().applicationContext

            NotificationUtils.scheduleTaskNotification(
                context = context,
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
            repository: TaskRepository,
            application: Application
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TaskDetailViewModel::class.java)) {
                    return TaskDetailViewModel(repository, application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}