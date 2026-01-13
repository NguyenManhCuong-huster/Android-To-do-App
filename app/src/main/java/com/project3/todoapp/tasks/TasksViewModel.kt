package com.project3.todoapp.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.Task
import com.project3.todoapp.data.TaskRepository
import com.project3.todoapp.notification.TaskNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class TasksViewModel(
    private val repository: TaskRepository,
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {

    // Luồng dữ liệu tự động cập nhật khi DB thay đổi
    val tasks: StateFlow<List<Task>> = repository.getTasksStream()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val filter = MutableStateFlow(Filter.ALL)

    val filteredTasks = combine(tasks, filter) { list, type ->
        when (type) {
            Filter.ALL -> list
            Filter.COMPLETED -> list.filter { it.isCompleted }
            Filter.PENDING -> list.filter { !it.isCompleted }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun setFilter(newFilter: Filter) {
        filter.value = newFilter
    }

    fun updateTaskCompletion(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskCompletion(taskId, isCompleted)
        }
    }

    // Xóa task
    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.deleteTask(id)
            taskNotificationManager.cancelNotification(id)
        }
    }

    fun syncData() {
        taskNotificationManager.showStatusNotification("Đang đồng bộ dữ liệu với Google Drive...")

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.sync()
        }
    }

    companion object {
        fun provideFactory(
            repository: TaskRepository,
            notificationManager: TaskNotificationManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TasksViewModel(repository, notificationManager) as T
            }
        }
    }
}