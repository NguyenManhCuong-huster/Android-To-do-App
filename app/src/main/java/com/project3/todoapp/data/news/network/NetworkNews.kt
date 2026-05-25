package com.project3.todoapp.data.news.network

import com.project3.todoapp.data.attachment.network.NetworkAttachment

/**
 * NetworkNews — DTO trung gian giữa repository và Retrofit.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Thêm `attachments`. NewsRepository sync vào attachment_dao trong cùng
 *    transaction refresh.
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
    val attachments: List<NetworkAttachment> = emptyList(),    // ← MỚI
)
