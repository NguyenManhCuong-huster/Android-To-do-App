package com.project3.todoapp.data.tasktag.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TaskTagCrossRefDAO {

    // 1. C || U: Create or Update - Gán một/nhiều nhãn cho một Task
    @Upsert
    suspend fun upsert(crossRef: LocalTaskTagCrossRef)

    @Upsert
    suspend fun upsertAll(crossRefs: List<LocalTaskTagCrossRef>)

    // 3. D: Delete - Gỡ một nhãn ra khỏi Task
    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun deleteSpecific(taskId: String, tagId: String)

    // D: Delete - Gỡ toàn bộ nhãn của một Task (Dùng khi xóa Task hoặc cập nhật lại toàn bộ nhãn)
    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun deleteAllTagsByTaskId(taskId: String)

    // D: Delete - Xóa mọi liên kết liên quan đến một nhãn (Dùng khi nhãn đó bị xóa khỏi hệ thống)
    @Query("DELETE FROM task_tag_cross_ref WHERE tagId = :tagId")
    suspend fun deleteAllTasksByTagId(tagId: String)
}