package com.project3.todoapp.data.news.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LocalNewsRecommendation — cache local của news_recommendations server-side.
 *
 * KHÔNG có `userId` ở client vì app single-user (1 device 1 user). Khi logout
 * sẽ clear toàn bộ bảng này cùng với news.
 *
 * `matchedKeywordsCsv`: lưu list keyword dưới dạng "K66,CNTT" (csv) để né
 * TypeConverter cho List<String>. Reason đã được server build sẵn nên client
 * chỉ cần render thẳng.
 *
 * FK đến news.id để khi news bị xoá khỏi local cache (sau refresh) thì
 * recommendation cũng tự xoá theo (CASCADE).
 */
@Entity(
    tableName = "news_recommendations",
    foreignKeys = [
        ForeignKey(
            entity        = LocalNews::class,
            parentColumns = ["id"],
            childColumns  = ["newsId"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["score"])],
)
data class LocalNewsRecommendation(
    @PrimaryKey val newsId: String,
    val score: Float,
    val reason: String?,
    val matchedKeywordsCsv: String,
    val generatedAt: Long,
    val isDismissed: Boolean = false,
)
