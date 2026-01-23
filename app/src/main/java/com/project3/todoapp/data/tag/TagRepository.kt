package com.project3.todoapp.data.tag

import kotlinx.coroutines.flow.Flow

interface TagRepository {
    suspend fun createTag(name: String, colorHex: String): String

    suspend fun updateTag(tagId: String, name: String, colorHex: String)

    suspend fun getTag(tagId: String): Tag?

    suspend fun deleteTag(tagId: String)

    fun getTagsStream(): Flow<List<Tag>>

    suspend fun getTags(): List<Tag>

    suspend fun getTagsByTask(taskId: String): List<Tag>


    suspend fun deleteAllTags()

    suspend fun sync()
}