package com.project3.todoapp.createtask

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.TaskRepository
import com.project3.todoapp.notification.NotificationUtils
import kotlinx.coroutines.launch

class CreateTaskViewModel(
    private val repository: TaskRepository,
    application: Application
) : AndroidViewModel(application) {

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
            val context = getApplication<Application>().applicationContext

            NotificationUtils.scheduleTaskNotification(
                context = context,
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
            application: Application
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreateTaskViewModel::class.java)) {
                    return CreateTaskViewModel(repository, application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}