package com.project.hustassistant.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.hustassistant.data.tag.Tag
import com.project.hustassistant.data.tag.TagRepository
import com.project.hustassistant.data.task.Task
import com.project.hustassistant.data.task.TaskRepository
import com.project.hustassistant.notification.TaskNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(
    private val repository: TaskRepository,
    private val tagRepository: TagRepository, // Cần thêm repo này để lấy danh sách tag cho bộ lọc
    private val taskNotificationManager: TaskNotificationManager
) : ViewModel() {

    // 1. Luồng dữ liệu gốc
    private val _rawTasks = repository.getTasksStream()

    // 2. Các luồng điều kiện lọc/sắp xếp
    private val _filterStatus = MutableStateFlow(Filter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTagId = MutableStateFlow<String?>(null) // null = All tags
    private val _sortOption = MutableStateFlow(SortOption.START_TIME_ASC)

    // 3. Luồng danh sách Tag (để hiển thị trên thanh lọc)
    val tags: StateFlow<List<Tag>> = tagRepository.getTagsStream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. LOGIC TỔNG HỢP (Combine)
    val filteredTasks: StateFlow<List<Task>> = combine(
        _rawTasks,
        _filterStatus,
        _searchQuery,
        _selectedTagId,
        _sortOption
    ) { tasks, status, query, tagId, sort ->

        // Bước 1: Lọc
        var result = tasks.filter { task ->
            // Lọc theo Status
            val matchesStatus = when (status) {
                Filter.ALL -> true
                Filter.COMPLETED -> task.isCompleted
                Filter.PENDING -> !task.isCompleted
            }

            // Lọc theo Search (Title hoặc Description)
            val matchesSearch = if (query.isBlank()) true else {
                task.title.contains(query, ignoreCase = true) ||
                        task.description.contains(query, ignoreCase = true)
            }

            // Lọc theo Tag
            val matchesTag = if (tagId == null) true else {
                task.tags.any { it.id == tagId }
            }

            matchesStatus && matchesSearch && matchesTag
        }

        // Bước 2: Sắp xếp
        result = when (sort) {
            SortOption.START_TIME_ASC -> result.sortedBy { it.start }
            SortOption.END_TIME_ASC -> result.sortedBy { it.end }
            SortOption.PRIORITY_DESC -> result.sortedByDescending { it.priority.value }
            SortOption.TITLE_ASC -> result.sortedBy { it.title }
        }

        result

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // --- CÁC HÀM SETTER ---
    fun setFilterStatus(newFilter: Filter) {
        _filterStatus.value = newFilter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTag(tagId: String?) {
        _selectedTagId.value = tagId
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    // --- CÁC HÀM TÁC VỤ ---
    fun updateTaskCompletion(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskCompletion(taskId, isCompleted)
            // Hoàn thành → huỷ nhắc (khỏi nhắc việc đã xong). Bỏ hoàn thành → đặt lại nhắc
            // theo giờ bắt đầu (idempotent, tự bỏ qua nếu mốc đã qua).
            if (isCompleted) {
                taskNotificationManager.cancelNotification(taskId)
            } else {
                repository.getTask(taskId)?.let { t ->
                    taskNotificationManager.scheduleTaskNotification(t.id, t.title, t.description, t.start)
                }
            }
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.deleteTask(id)
            taskNotificationManager.cancelNotification(id)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.sync()
        }
    }

    companion object {
        fun provideFactory(
            taskRepository: TaskRepository,
            tagRepository: TagRepository,
            notificationManager: TaskNotificationManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TasksViewModel(taskRepository, tagRepository, notificationManager) as T
            }
        }
    }
}