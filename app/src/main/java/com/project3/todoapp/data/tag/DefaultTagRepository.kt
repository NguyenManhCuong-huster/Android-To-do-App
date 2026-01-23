package com.project3.todoapp.data.tag

import com.project3.todoapp.data.tag.local.TagDAO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class DefaultTagRepository(
    private val tagDao: TagDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TagRepository {

    override suspend fun createTag(name: String, colorHex: String): String {
        return withContext(dispatcher) {
            val tagId = UUID.randomUUID().toString()
            val tag = Tag(
                id = tagId,
                tagName = name,
                colorHex = colorHex,
                modTime = System.currentTimeMillis()
            )
            tagDao.upsert(tag.toLocal())
            tagId
        }
    }

    override suspend fun updateTag(tagId: String, name: String, colorHex: String) {
        withContext(dispatcher) {
            val tag = Tag(
                id = tagId,
                tagName = name,
                colorHex = colorHex,
                modTime = System.currentTimeMillis()
            )
            tagDao.upsert(tag.toLocal())
        }
    }

    override suspend fun getTag(tagId: String): Tag? {
        return withContext(dispatcher) {
            tagDao.getById(tagId)?.toExternal()
        }
    }

    override suspend fun deleteTag(tagId: String) {
        withContext(dispatcher) {
            tagDao.deleteById(tagId)
        }
    }

    override fun getTagsStream(): Flow<List<Tag>> {
        return tagDao.observeAll().map { localTags ->
            localTags.toExternal()
        }
    }

    override suspend fun getTags(): List<Tag> {
        return withContext(dispatcher) {
            tagDao.getAll().toExternal()
        }
    }

    override suspend fun getTagsByTask(taskId: String): List<Tag> {
        return withContext(dispatcher) {
            tagDao.getTagsForTask(taskId).toExternal()
        }
    }

    override suspend fun deleteAllTags() {
        withContext(dispatcher) {
            tagDao.deleteAll()
        }
    }

    override suspend fun sync() {
        // Bỏ qua nội dung theo yêu cầu
    }
}