package com.project3.todoapp.data.news.network

/**
 * NetworkDataSource — interface chung cho mọi nguồn network của News.
 *
 * Khác NetworkDataSource của Task ở chỗ chỉ có 1 method [loadNews] —
 * News read-only, không có create/update/delete.
 *
 * Implementation thật: [NewsNetworkDataSource] (REST qua [NewsApi]).
 *
 * Quy ước: method PHẢI tự bắt exception, trả emptyList() khi lỗi.
 */
interface NetworkDataSource {
    suspend fun loadNews(): List<NetworkNews>
}
