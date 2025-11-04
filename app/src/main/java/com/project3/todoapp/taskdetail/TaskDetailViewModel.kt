package com.project3.todoapp.taskdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.Task
import com.project3.todoapp.data.TaskRepository
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    private val repository: TaskRepository
) : ViewModel() {
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _taskUpdated = MutableLiveData<Boolean>()
    val taskUpdated: LiveData<Boolean> = _taskUpdated

    suspend fun getTask(taskId: String): Task? {
        return repository.getTask(taskId)
    }

    // Cập nhật task
    fun updateTask(id: String, title: String, description: String, start: Long, end: Long) {
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            _taskUpdated.value = false
            return
        }
        viewModelScope.launch {
            repository.updateTask(id, title, description, start, end)
            _taskUpdated.value = true
        }
    }

    companion object {
        fun provideFactory(
            repository: TaskRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TaskDetailViewModel::class.java)) {
                    return TaskDetailViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}