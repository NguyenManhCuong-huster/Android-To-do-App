package com.project3.todoapp.data.news.network

import com.project3.todoapp.data.attachment.toNetwork
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * NewsNetworkDataSource — impl của [NetworkDataSource].
 *
 * THAY ĐỔI 2026-05-23: Map `attachments` từ DTO → NetworkAttachment.
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
