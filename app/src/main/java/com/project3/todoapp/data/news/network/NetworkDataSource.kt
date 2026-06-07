package com.project3.todoapp.data.news.network

/**
 * NetworkDataSource — interface chung cho mọi nguồn network của News.
 *
 * News read-only nên không có create/update/delete. Method PHẢI tự bắt
 * exception, trả về emptyList()/false khi lỗi (offline-first pattern).
 *
 * Implementation thật: [NewsNetworkDataSource] (REST qua [NewsApi]).
 */
interface NetworkDataSource {

    /** Pull danh sách news (kèm attachments). EmptyList nếu lỗi. */
    suspend fun loadNews(): List<NetworkNews>

    /**
     * Lấy danh sách đề xuất từ server (kèm score/reason).
     * EmptyList nếu lỗi HOẶC user chưa có profile.
     */
    suspend fun loadRecommendations(limit: Int = 50): List<NetworkNews>

    /** Force server recompute cache. True nếu OK. */
    suspend fun refreshRecommendations(): Boolean

    /** User dismiss 1 recommendation. True nếu OK. */
    suspend fun dismissRecommendation(newsId: String): Boolean
}
