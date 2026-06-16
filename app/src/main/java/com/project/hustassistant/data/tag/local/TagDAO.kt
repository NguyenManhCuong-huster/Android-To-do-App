package com.project.hustassistant.data.tag.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * TagDAO — Room interface.
 *
 * Quy ước:
 *  - Mọi truy vấn READ cho UI tự lọc isDeleted=0 (không lộ tag đã xoá mềm).
 *  - Có thêm các query cho SYNC: getDirtyTags, markSynced, reassignId, hardDeleteById.
 */
@Dao
interface TagDAO {

    // ─── READ (cho UI) ─────────────────────────────────
    /** Quan sát tag chưa bị xoá — dùng cho UI (Flow tự cập nhật). */
    @Query("SELECT * FROM tag WHERE isDeleted = 0 ORDER BY tagName ASC")
    fun observeAll(): Flow<List<LocalTag>>

    @Query("SELECT * FROM tag WHERE isDeleted = 0")
    suspend fun getAll(): List<LocalTag>

    @Query(
        """
        SELECT tag.* FROM tag 
        INNER JOIN task_tag_cross_ref ON tag.id = task_tag_cross_ref.tagId 
        WHERE task_tag_cross_ref.taskId = :taskId AND tag.isDeleted = 0
        """
    )
    suspend fun getTagsForTask(taskId: String): List<LocalTag>

    /** Lấy theo id — KHÔNG lọc isDeleted để sync có thể đọc cả tag đã xoá. */
    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun getById(id: String): LocalTag?

    @Query("SELECT EXISTS(SELECT 1 FROM tag WHERE tagName = :name AND isDeleted = 0 LIMIT 1)")
    suspend fun isTagNameExists(name: String): Boolean

    // ─── WRITE ─────────────────────────────────────────
    @Upsert
    suspend fun upsert(tag: LocalTag)

    @Upsert
    suspend fun upsertAll(tags: List<LocalTag>)

    /** Soft-delete: ẩn khỏi UI và đánh dấu cần đẩy delete lên server. */
    @Query("UPDATE tag SET isDeleted = 1, isDirty = 1, modTime = :modTime WHERE id = :id")
    suspend fun softDelete(id: String, modTime: Long)

    /** Hard-delete: chỉ dùng sau khi sync đã xác nhận server cũng xoá, hoặc khi reset. */
    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun hardDeleteById(id: String)

    @Query("DELETE FROM tag")
    suspend fun deleteAll()

    // ─── SYNC HELPERS ──────────────────────────────────
    /** Lấy tag CẦN PUSH (đã thay đổi local nhưng chưa lên server). */
    @Query("SELECT * FROM tag WHERE isDirty = 1")
    suspend fun getDirtyTags(): List<LocalTag>

    /** Đánh dấu đã đồng bộ xong với server. */
    @Query("UPDATE tag SET isDirty = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    /**
     * Đổi id của tag — dùng khi server cấp id mới cho tag tạo offline.
     *
     * QUAN TRỌNG: yêu cầu task_tag_cross_ref khai báo
     *   `onUpdate = ForeignKey.CASCADE` cho cột tagId
     * để các cross-ref tự đổi theo (xem LocalTaskTagCrossRef).
     */
    @Query("UPDATE tag SET id = :newId, isDirty = 0 WHERE id = :oldId")
    suspend fun reassignId(oldId: String, newId: String)
}
