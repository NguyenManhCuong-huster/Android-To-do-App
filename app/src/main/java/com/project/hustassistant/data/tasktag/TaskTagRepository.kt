package com.project.hustassistant.data.tasktag

interface TaskTagRepository {
    suspend fun updateTagsToTask(taskId: String, tagIds: List<String>)

}