package com.project.hustassistant.data.tasktag

import com.project.hustassistant.data.task.local.TaskDAO
import com.project.hustassistant.data.tasktag.local.LocalTaskTagCrossRef
import com.project.hustassistant.data.tasktag.local.TaskTagCrossRefDAO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultTaskTagRepository(
    private val taskTagCrossRefDao: TaskTagCrossRefDAO,
    private val taskDao: TaskDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TaskTagRepository {

    override suspend fun updateTagsToTask(taskId: String, tagIds: List<String>) {
        withContext(dispatcher) {
            // 1. Xóa sạch các liên kết cũ của Task này (cross-ref local là hard delete+insert;
            //    server sẽ tự đối chiếu tombstone khi nhận tag_ids mới).
            taskTagCrossRefDao.deleteAllTagsByTaskId(taskId)

            // 2. Chèn lại liên kết mới.
            val now = System.currentTimeMillis()
            val newRefs = tagIds.map { tagId ->
                LocalTaskTagCrossRef(taskId = taskId, tagId = tagId, modTime = now)
            }
            taskTagCrossRefDao.upsertAll(newRefs)

            // 3. QUAN TRỌNG: đánh dấu Task dirty để SyncManager đẩy `tag_ids` mới lên
            //    server (đổi tag không tự làm Task dirty → trước đây thay đổi gán tag
            //    không được đồng bộ). Bump modTime để LWW ở server chấp nhận.
            taskDao.markDirty(taskId, now)
        }
    }
}
