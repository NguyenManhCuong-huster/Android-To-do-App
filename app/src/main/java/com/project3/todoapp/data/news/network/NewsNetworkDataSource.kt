package com.project3.todoapp.data.news.network

import com.project3.todoapp.data.attachment.toNetwork
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * NewsNetworkDataSource — impl của [NetworkDataSource].
 *
 * Mọi method tự bắt exception, fallback emptyList/false để repository
 * không cần try/catch khi gọi (offline-first pattern).
 */
class NewsNetworkDataSource(
    private val newsApi: NewsApi,
) : NetworkDataSource {

    override suspend fun loadNews(): List<NetworkNews> = try {
        newsApi.list(page = 1, limit = 200)
            .data
            .map { it.toNetworkNews() }
    } catch (_: Exception) {
        emptyList()
    }

    // ─── Recommendations ─────────────────────────────────────

    override suspend fun loadRecommendations(limit: Int): List<NetworkNews> = try {
        newsApi.listRecommendations(limit = limit)
            .data
            .map { it.toNetworkNews() }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun refreshRecommendations(): Boolean = try {
        newsApi.refreshRecommendations().success
    } catch (_: Exception) {
        false
    }

    override suspend fun dismissRecommendation(newsId: String): Boolean = try {
        newsApi.dismissRecommendation(newsId).success
    } catch (_: Exception) {
        false
    }

    override suspend fun loadNewsById(id: String): NetworkNews? = try {
        newsApi.getById(id).data?.toNetworkNews()
    } catch (_: Exception) {
        null
    }

    // ─── DTO → NetworkNews ────────────────────────────────────

    private fun NewsDto.toNetworkNews() = NetworkNews(
        id          = id,
        kind        = kind,
        title       = title,
        summary     = summary,
        articleUrl  = article_url,
        imageUrl    = image_url,
        tag         = tag,
        publishedAt = fromIso(published_at),
        modTime     = fromIso(mod_time),
        sourceName  = source_name,
        attachments = attachments?.toNetwork().orEmpty(),

        recommendScore           = score,
        recommendReason          = reason,
        recommendMatchedKeywords = matched_keywords.orEmpty(),
    )

    private fun fromIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
