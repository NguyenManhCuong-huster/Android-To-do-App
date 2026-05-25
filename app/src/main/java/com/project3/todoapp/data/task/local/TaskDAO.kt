package com.project3.todoapp.data.task.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * TaskDAO — Room interface.
 *
 * Quy ước (giống TagDAO):
 *  - Mọi truy vấn READ cho UI tự lọc isDeleted=0 (không lộ task đã xoá mềm).
 *  - Có thêm các query cho SYNC: getDirtyTasks, markSynced, reassignId, hardDeleteById.
 *
 *  ⚠️ KHÔNG còn `syncTasks(deleteAll + upsertAll)` nữa — pattern đó wipe luôn isDirty,
 *  khiến các thay đổi local chưa push bị mất, và là nguyên nhân gốc của bug nhân bản
 *  task. Sync giờ làm per-record qua DefaultTaskRepository.pushLocalChanges /
 *  pullRemoteChanges.
 */
@Dao
interface TaskDAO {

    // ─── READ (cho UI) ─────────────────────────────────
    /** Quan sát task chưa bị xoá — dùng cho UI (Flow tự cập nhật). */
    @Query("SELECT * FROM task WHERE isDeleted = 0 ORDER BY start ASC")
    fun observeAll(): Flow<List<LocalTask>>

    @Query("SELECT * FROM task WHERE isDeleted = 0")
    suspend fun getAll(): List<LocalTask>

    /** Lấy theo id — KHÔNG lọc isDeleted để sync có thể đọc cả task đã xoá. */
    @Query("SELECT * FROM task WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): LocalTask?

    @Transaction
    @Query("SELECT * FROM task WHERE id = :taskId AND isDeleted = 0")
    suspend fun getTaskWithTagsById(taskId: String): LocalTaskWithTags?

    @Transaction
    @Query("SELECT * FROM task WHERE isDeleted = 0 ORDER BY start ASC")
    fun observeTasksWithTags(): Flow<List<LocalTaskWithTags>>

    @Query(
        """
        SELECT task.* FROM task
        INNER JOIN task_tag_cross_ref ON task.id = task_tag_cross_ref.taskId
        WHERE task_tag_cross_ref.tagId = :tagId AND task.isDeleted = 0
        """
    )
    fun getTasksForTag(tagId: String): Flow<List<LocalTask>>

    // ─── WRITE ─────────────────────────────────────────
    @Upsert
    suspend fun upsertTask(task: LocalTask)

    @Upsert
    suspend fun upsertAll(tasks: List<LocalTask>)

    @Query("UPDATE task SET isCompleted = :completed, isDirty = 1, modTime = :modTime WHERE id = :taskId")
    suspend fun updateTaskCompletion(taskId: String, completed: Boolean, modTime: Long)

    /** Soft-delete: ẩn khỏi UI và đánh dấu cần đẩy DELETE lên server. */
    @Query("UPDATE task SET isDeleted = 1, isDirty = 1, modTime = :modTime WHERE id = :id")
    suspend fun softDelete(id: String, modTime: Long)

    /** Hard-delete: chỉ dùng sau khi sync đã xác nhận server cũng xoá, hoặc khi reset. */
    @Query("DELETE FROM task WHERE id = :id")
    suspend fun hardDeleteById(id: String)

    /** Xoá toàn bộ local (gọi khi đăng xuất / đổi user). KHÔNG đụng tới server. */
    @Query("DELETE FROM task")
    suspend fun deleteAll()

    // ─── SYNC HELPERS ──────────────────────────────────
    /** Lấy task CẦN PUSH (đã thay đổi local nhưng chưa lên server). */
    @Query("SELECT * FROM task WHERE isDirty = 1")
    suspend fun getDirtyTasks(): List<LocalTask>

    /** Đánh dấu đã đồng bộ xong với server. */
    @Query("UPDATE task SET isDirty = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    /**
     * Đổi id của task — dùng khi server cấp id mới cho task tạo offline.
     *
     * QUAN TRỌNG: yêu cầu task_tag_cross_ref khai báo
     *   `onUpdate = ForeignKey.CASCADE` cho cột taskId
     * để các cross-ref tự đổi theo (đã có sẵn trong LocalTaskTagCrossRef).
     */
    @Query("UPDATE task SET id = :newId, isDirty = 0 WHERE id = :oldId")
    suspend fun reassignId(oldId: String, newId: String)
}
