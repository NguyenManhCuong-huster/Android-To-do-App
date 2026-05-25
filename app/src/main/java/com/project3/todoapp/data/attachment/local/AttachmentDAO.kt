package com.project3.todoapp.data.attachment.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * AttachmentDAO — query attachments theo owner.
 *
 * Pattern giống NewsDAO: read-only từ server, không có dirty flag.
 * [localCachedPath] tuy là client-write nhưng coi như cache "kết quả phụ"
 * — không bao giờ push lên server.
 */
@Dao
interface AttachmentDAO {

    /** Quan sát attachments của 1 email cụ thể (cho ThreadMessageAdapter). */
    @Query(
        """
        SELECT * FROM attachment
        WHERE ownerType = 'EMAIL' AND ownerId = :emailId
        ORDER BY modTime ASC
        """
    )
    fun observeForEmail(emailId: String): Flow<List<LocalAttachment>>

    /** Quan sát attachments của 1 news cụ thể (cho NewsDetailActivity). */
    @Query(
        """
        SELECT * FROM attachment
        WHERE ownerType = 'NEWS' AND ownerId = :newsId
        ORDER BY modTime ASC
        """
    )
    fun observeForNews(newsId: String): Flow<List<LocalAttachment>>

    /** Suspend version cho one-shot read. */
    @Query(
        """
        SELECT * FROM attachment
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        ORDER BY modTime ASC
        """
    )
    suspend fun getForOwner(ownerType: String, ownerId: String): List<LocalAttachment>

    @Query("SELECT * FROM attachment WHERE id = :id")
    suspend fun getById(id: String): LocalAttachment?

    @Upsert
    suspend fun upsertAll(items: List<LocalAttachment>)

    @Query("UPDATE attachment SET localCachedPath = :path WHERE id = :id")
    suspend fun updateLocalPath(id: String, path: String?)

    /** Xoá tất cả attachments của 1 owner (dùng khi server trả 0 attachments → cleanup). */
    @Query("DELETE FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: String)

    /** Xoá attachments của 1 owner mà KHÔNG có trong list `keepIds`. */
    @Query(
        """
        DELETE FROM attachment
        WHERE ownerType = :ownerType
          AND ownerId   = :ownerId
          AND id NOT IN (:keepIds)
        """
    )
    suspend fun deleteForOwnerNotIn(ownerType: String, ownerId: String, keepIds: List<String>)

    /** Atomic: replace attachments của 1 owner. */
    @Transaction
    suspend fun replaceForOwner(ownerType: String, ownerId: String, items: List<LocalAttachment>) {
        if (items.isEmpty()) {
            deleteForOwner(ownerType, ownerId)
        } else {
            upsertAll(items)
            deleteForOwnerNotIn(ownerType, ownerId, items.map { it.id })
        }
    }

    @Query("DELETE FROM attachment")
    suspend fun deleteAll()
}
