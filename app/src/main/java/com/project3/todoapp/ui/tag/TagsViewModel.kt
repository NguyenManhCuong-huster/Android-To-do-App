package com.project3.todoapp.ui.tag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.tag.Tag
import com.project3.todoapp.data.tag.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(private val repository: TagRepository) : ViewModel() {

    // Lấy danh sách Tag real-time
    val tags: StateFlow<List<Tag>> = repository.getTagsStream()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun createTag(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.createTag(name, colorHex)
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            repository.deleteTag(tagId)
        }
    }

    companion object {
        fun provideFactory(repository: TagRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TagsViewModel(repository) as T
                }
            }
    }
}