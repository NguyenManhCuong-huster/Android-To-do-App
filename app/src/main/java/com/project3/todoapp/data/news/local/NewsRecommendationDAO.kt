package com.project3.todoapp.data.news.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * NewsRecommendationDAO — DAO cho bảng news_recommendations.
 *
 * `observeRecommended` JOIN với bảng news để trả về full data + score/reason.
 * Sắp xếp theo score DESC để news match cao nhất lên đầu. Chỉ lấy row chưa
 * dismiss và news chưa bị xoá khỏi cache.
 */
@Dao
interface NewsRecommendationDAO {

    /**
     * Stream news đã recommend, ORDER BY score DESC.
     * Trả về projection [LocalNewsWithRecommendation] = LocalNews + 3 column extra.
     */
    @Transaction
    @Query(
        """
        SELECT n.*, r.score AS score, r.reason AS reason,
               r.matchedKeywordsCsv AS matchedKeywordsCsv
        FROM news_recommendations r
        INNER JOIN news n ON n.id = r.newsId
        WHERE r.isDismissed = 0
        ORDER BY r.score DESC, n.publishedAt DESC
        """
    )
    fun observeRecommended(): Flow<List<LocalNewsWithRecommendation>>

    @Query("SELECT MAX(generatedAt) FROM news_recommendations")
    suspend fun getLatestGeneratedAt(): Long?

    @Upsert
    suspend fun upsertAll(items: List<LocalNewsRecommendation>)

    @Query("DELETE FROM news_recommendations")
    suspend fun deleteAll()

    @Query("UPDATE news_recommendations SET isDismissed = 1 WHERE newsId = :newsId")
    suspend fun dismiss(newsId: String)

    /**
     * Atomic replace: wipe all + upsert all trong 1 transaction.
     * UI Flow không thấy danh sách rỗng tạm thời giữa 2 thao tác.
     */
    @Transaction
    suspend fun replaceAll(items: List<LocalNewsRecommendation>) {
        deleteAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}

/**
 * Projection cho query JOIN: embed LocalNews + 3 column từ news_recommendations.
 * Mapper `toExternal()` ở ModelMappingExt sẽ build News kèm recommendation fields.
 */
data class LocalNewsWithRecommendation(
    @Embedded val news: LocalNews,
    val score: Float,
    val reason: String?,
    val matchedKeywordsCsv: String,
)
