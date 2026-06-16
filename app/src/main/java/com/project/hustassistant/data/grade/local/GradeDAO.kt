package com.project.hustassistant.data.grade.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * GradeDAO — Room interface (giống TagDAO).
 *
 * Quy ước:
 *  - Query READ cho UI tự lọc isDeleted=0.
 *  - Có thêm các query cho SYNC: getDirtyGrades, markSynced, reassignId, hardDeleteById.
 */
@Dao
interface GradeDAO {

    // ─── READ (cho UI) ─────────────────────────────────
    @Query("SELECT * FROM grade WHERE isDeleted = 0 ORDER BY semester ASC, courseCode ASC")
    fun observeAll(): Flow<List<LocalGrade>>

    @Query("SELECT * FROM grade WHERE isDeleted = 0 ORDER BY semester ASC, courseCode ASC")
    suspend fun getAll(): List<LocalGrade>

    /** Lấy theo id — KHÔNG lọc isDeleted để sync đọc được cả record đã xoá. */
    @Query("SELECT * FROM grade WHERE id = :id")
    suspend fun getById(id: String): LocalGrade?

    // ─── WRITE ─────────────────────────────────────────
    @Upsert
    suspend fun upsert(grade: LocalGrade)

    @Upsert
    suspend fun upsertAll(grades: List<LocalGrade>)

    /** Soft-delete: ẩn khỏi UI và đánh dấu cần đẩy delete lên server. */
    @Query("UPDATE grade SET isDeleted = 1, isDirty = 1, modTime = :modTime WHERE id = :id")
    suspend fun softDelete(id: String, modTime: Long)

    /** Hard-delete: chỉ dùng sau khi sync xác nhận server cũng xoá, hoặc khi reset. */
    @Query("DELETE FROM grade WHERE id = :id")
    suspend fun hardDeleteById(id: String)

    @Query("DELETE FROM grade")
    suspend fun deleteAll()

    // ─── SYNC HELPERS ──────────────────────────────────
    @Query("SELECT * FROM grade WHERE isDirty = 1")
    suspend fun getDirtyGrades(): List<LocalGrade>

    @Query("UPDATE grade SET isDirty = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    /** Đổi id của grade — dùng khi server cấp id mới cho grade tạo offline. */
    @Query("UPDATE grade SET id = :newId, isDirty = 0 WHERE id = :oldId")
    suspend fun reassignId(oldId: String, newId: String)
}
