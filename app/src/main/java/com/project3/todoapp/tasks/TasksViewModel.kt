package com.project3.todoapp.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.Task
import com.project3.todoapp.data.TaskRepository
import com.project3.todoapp.notification.NotificationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class TasksViewModel(
    private val repository: TaskRepository,
    application: Application
) : AndroidViewModel(application) {

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
            val context = getApplication<Application>().applicationContext
            NotificationUtils.cancelTaskNotification(context, id)
        }
    }

    companion object {
        fun provideFactory(
            repository: TaskRepository,
            application: Application
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TasksViewModel::class.java)) {
                    return TasksViewModel(repository, application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}