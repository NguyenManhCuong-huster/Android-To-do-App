package com.project.hustassistant.data.userinfo.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * UserInfoDAO — thao tác trên 1 row duy nhất (PK = "me").
 *
 * `observe()` trả Flow<LocalUserInfo?> — null nếu chưa từng load (lần đầu mở app
 * khi chưa sync) → ViewModel hiện loading.
 */
@Dao
interface UserInfoDAO {

    @Query("SELECT * FROM user_info WHERE id = :id LIMIT 1")
    fun observe(id: String = LocalUserInfo.SINGLETON_ID): Flow<LocalUserInfo?>

    @Query("SELECT * FROM user_info WHERE id = :id LIMIT 1")
    suspend fun get(id: String = LocalUserInfo.SINGLETON_ID): LocalUserInfo?

    @Upsert
    suspend fun upsert(info: LocalUserInfo)

    @Query("UPDATE user_info SET isDirty = 0, modTime = :modTime WHERE id = :id")
    suspend fun markSynced(modTime: Long, id: String = LocalUserInfo.SINGLETON_ID)

    @Query("DELETE FROM user_info WHERE id = :id")
    suspend fun clear(id: String = LocalUserInfo.SINGLETON_ID)
}
