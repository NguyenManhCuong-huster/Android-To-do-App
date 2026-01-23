package com.project3.todoapp.data.tasktag

import com.project3.todoapp.data.tasktag.local.LocalTaskTagCrossRef
import com.project3.todoapp.data.tasktag.local.TaskTagCrossRefDAO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultTaskTagRepository(
    private val taskTagCrossRefDao: TaskTagCrossRefDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TaskTagRepository {

    override suspend fun updateTagsToTask(taskId: String, tagIds: List<String>) {
        withContext(dispatcher) {
            // Thực hiện trong Transaction nếu có thể (hoặc gọi tuần tự)
            // 1. Xóa sạch các liên kết cũ của Task này
            taskTagCrossRefDao.deleteAllTagsByTaskId(taskId)

            // 2. Chuyển đổi list ID thành list Entity và chèn vào database
            val newRefs = tagIds.map { tagId ->
                LocalTaskTagCrossRef(
                    taskId = taskId,
                    tagId = tagId,
                    modTime = System.currentTimeMillis()
                )
            }
            taskTagCrossRefDao.upsertAll(newRefs)
        }
    }
}