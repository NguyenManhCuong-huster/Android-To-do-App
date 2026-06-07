package com.project3.todoapp.data.news.network

import com.project3.todoapp.data.attachment.network.NetworkAttachment

/**
 * NetworkNews — DTO trung gian giữa repository và Retrofit.
 *
 * 3 field `recommendXxx` chỉ có giá trị khi instance đến từ
 * /api/news/recommendations. Với /api/news thường thì luôn null/empty.
 */
data class NetworkNews(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String?,
    val articleUrl: String?,
    val imageUrl: String?,
    val tag: String?,
    val publishedAt: Long,
    val modTime: Long,
    val sourceName: String?,
    val attachments: List<NetworkAttachment> = emptyList(),

    // ─── MỚI: optional, chỉ có nếu response từ /api/news/recommendations ───
    val recommendScore: Float? = null,
    val recommendReason: String? = null,
    val recommendMatchedKeywords: List<String> = emptyList(),
)
