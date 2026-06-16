package com.project.hustassistant.data.news.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * NewsDAO — Room interface cho bảng news.
 *
 * News ở client là read-only, nên DAO đơn giản hơn TaskDAO:
 *  - Không có softDelete / getDirty / markSynced / reassignId.
 *  - Có [replaceAll] để refresh ghi đè toàn bộ trong 1 transaction
 *    (xoá all + upsert all → atomic, UI không thấy trạng thái rỗng tạm thời).
 *
 * Sắp xếp mặc định: publishedAt DESC NULLS LAST (mới nhất lên đầu),
 * tie-break bằng modTime DESC.
 */
@Dao
interface NewsDAO {

    // ─── READ ──────────────────────────────────────────
    @Query("SELECT * FROM news ORDER BY publishedAt DESC, modTime DESC")
    fun observeAll(): Flow<List<LocalNews>>

    @Query("SELECT * FROM news WHERE kind = :kind ORDER BY publishedAt DESC, modTime DESC")
    fun observeByKind(kind: String): Flow<List<LocalNews>>

    @Query("SELECT * FROM news WHERE tag = :tag ORDER BY publishedAt DESC, modTime DESC")
    fun observeByTag(tag: String): Flow<List<LocalNews>>

    @Query(
        """
        SELECT * FROM news
        WHERE kind = :kind AND tag = :tag
        ORDER BY publishedAt DESC, modTime DESC
        """
    )
    fun observeByKindAndTag(kind: String, tag: String): Flow<List<LocalNews>>

    /**
     * Search trong title hoặc summary. Param dùng dạng `%keyword%`.
     */
    @Query(
        """
        SELECT * FROM news
        WHERE title LIKE :query OR summary LIKE :query
        ORDER BY publishedAt DESC, modTime DESC
        """
    )
    fun search(query: String): Flow<List<LocalNews>>

    @Query("SELECT * FROM news WHERE id = :id")
    suspend fun getById(id: String): LocalNews?

    @Query("SELECT COUNT(*) FROM news")
    suspend fun count(): Int

    // ─── WRITE ─────────────────────────────────────────
    @Upsert
    suspend fun upsertAll(items: List<LocalNews>)

    @Query("DELETE FROM news")
    suspend fun deleteAll()

    /**
     * Atomic refresh: wipe all rồi upsert all trong 1 transaction.
     * Đảm bảo UI Flow không thấy danh sách rỗng tạm thời giữa 2 thao tác.
     */
    @Transaction
    suspend fun replaceAll(items: List<LocalNews>) {
        deleteAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}
