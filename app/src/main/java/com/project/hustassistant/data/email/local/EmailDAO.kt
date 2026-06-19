package com.project.hustassistant.data.email.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * EmailDAO — tất cả query thread-aware.
 *
 * 2 query đặc biệt cho thread:
 *
 * 1. observeThreadHeads(q) — danh sách email trong UI: dedup theo
 *    gmailThreadId, chỉ giữ message MỚI NHẤT mỗi thread.
 *    SQL: WHERE id IN (SELECT id của message mới nhất per thread).
 *
 * 2. getThreadUpTo(threadId, accountId, untilEpoch) — load thread cho
 *    màn đọc: WHERE gmailThreadId = ? AND accountId = ? AND receivedAt <= ?,
 *    ORDER BY receivedAt ASC.
 */
@Dao
interface EmailDAO {

    /**
     * Observe danh sách thread (1 row = 1 thread, đại diện = message mới nhất).
     *
     * Bước 1 (subquery): tìm id của message có MAX(receivedAt) trong mỗi thread —
     *   GROUP BY COALESCE(gmailThreadId, id) (nếu thread null thì coi mỗi message
     *   là thread riêng).
     * Bước 2 (outer): lấy full row + filter q + order theo thời gian.
     *
     * SQLite không hỗ trợ DISTINCT ON như Postgres → dùng JOIN với MAX subquery.
     */
    @Query(
        """
        SELECT * FROM email
        WHERE id IN (
            SELECT id FROM email e1
            WHERE receivedAt = (
                SELECT MAX(receivedAt) FROM email e2
                WHERE COALESCE(e2.gmailThreadId, e2.id) = COALESCE(e1.gmailThreadId, e1.id)
            )
        )
        AND (:q IS NULL
             OR subject LIKE '%' || :q || '%'
             OR sender  LIKE '%' || :q || '%'
             OR snippet LIKE '%' || :q || '%')
        ORDER BY receivedAt DESC
        """
    )
    fun observeThreadHeads(q: String?): Flow<List<LocalEmail>>

    /**
     * Lấy các message cùng thread, receivedAt <= untilEpoch.
     * Cùng accountId để tránh trộn thread của account khác (rare nhưng có thể
     * xảy ra với email forward giữa 2 account).
     *
     * Trả ASC để UI render từ cũ → mới.
     */
    @Query(
        """
        SELECT * FROM email
        WHERE gmailThreadId = :threadId
          AND accountId     = :accountId
          AND receivedAt   <= :untilEpoch
        ORDER BY receivedAt ASC
        """
    )
    suspend fun getThreadUpTo(
        threadId: String,
        accountId: String,
        untilEpoch: Long,
    ): List<LocalEmail>

    @Query("SELECT * FROM email ORDER BY receivedAt DESC")
    suspend fun getAll(): List<LocalEmail>

    @Query("SELECT * FROM email WHERE id = :id")
    suspend fun getById(id: String): LocalEmail?

    @Upsert
    suspend fun upsertAll(emails: List<LocalEmail>)

    @Query("DELETE FROM email WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>)

    @Query("UPDATE email SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("DELETE FROM email")
    suspend fun deleteAll()
}
