package com.project3.todoapp.createtask

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.TaskRepository
import kotlinx.coroutines.launch

class CreateTaskViewModel(
    private val repository: TaskRepository
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
            repository.createTask(title, description, start, end)
            _taskCreated.value = true
        }
    }
}