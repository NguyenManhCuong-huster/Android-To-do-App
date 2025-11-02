package com.project3.todoapp.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.Task
import com.project3.todoapp.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class TasksViewModel(
    private val repository: TaskRepository
) : ViewModel() {

    // Luồng dữ liệu tự động cập nhật khi DB thay đổi
    val tasks: StateFlow<List<Task>> = repository.getTasksStream()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun updateTaskCompletion(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskCompletion(taskId, isCompleted)
        }
    }

    // Xóa task
    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }
}

