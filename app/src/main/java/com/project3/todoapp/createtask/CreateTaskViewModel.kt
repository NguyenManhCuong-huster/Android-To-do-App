package com.project3.todoapp.createtask

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.project3.todoapp.data.TaskRepository

class CreateTaskViewModel(
    private val repository: TaskRepository
) : ViewModel() {
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _taskCreated = MutableLiveData<Boolean>()
    val taskCreated: LiveData<Boolean> = _taskCreated

    suspend fun createTask(title: String, description: String, start: Long, end: Long): String? {
        if (start > end) {
            _errorMessage.value = "Start time must be before end time"
            _taskCreated.value = false
            return null
        }
        _taskCreated.value = true
        return repository.createTask(title, description, start, end)
    }

    companion object {
        fun provideFactory(
            repository: TaskRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreateTaskViewModel::class.java)) {
                    return CreateTaskViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}