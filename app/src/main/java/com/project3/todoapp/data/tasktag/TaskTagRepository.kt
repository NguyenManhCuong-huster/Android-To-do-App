package com.project3.todoapp.data.tasktag

interface TaskTagRepository {
    suspend fun updateTagsToTask(taskId: String, tagIds: List<String>)

}