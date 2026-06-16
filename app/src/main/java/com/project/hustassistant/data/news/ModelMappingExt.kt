package com.project.hustassistant.data.news

import com.project.hustassistant.data.news.local.LocalNews
import com.project.hustassistant.data.news.local.LocalNewsRecommendation
import com.project.hustassistant.data.news.local.LocalNewsWithRecommendation
import com.project.hustassistant.data.news.network.NetworkNews

/*
 * ModelMappingExt — chuyển đổi giữa các model:
 *
 *      LocalNews (Room) ──► News (external) ◄── NetworkNews (API)
 *
 * Khác Task: News chỉ có 1 chiều LOCAL → EXTERNAL và NETWORK → LOCAL
 *  - Không có Task → Local (vì client không tạo News)
 *  - Không có Local → Network (vì client không push News)
 *
 * THÊM MỚI cho recommendations:
 *  - NetworkNews → LocalNewsRecommendation (chỉ khi NetworkNews đến từ /recommendations)
 *  - LocalNewsWithRecommendation → News (mang theo score/reason để UI render)
 */

// ─── LocalNews → News ────────────────────────────────
fun LocalNews.toExternal() = News(
    id          = id,
    kind        = NewsKind.fromServer(kind),
    title       = title,
    summary     = summary,
    articleUrl  = articleUrl,
    imageUrl    = imageUrl,
    tag         = tag,
    publishedAt = publishedAt,
    modTime     = modTime,
    sourceName  = sourceName,
)

@JvmName("localListToExternal")
fun List<LocalNews>.toExternal() = map(LocalNews::toExternal)

// ─── NetworkNews → LocalNews (khi pull về từ server) ───
fun NetworkNews.toLocal() = LocalNews(
    id          = id,
    kind        = kind.uppercase(),
    title       = title,
    summary     = summary,
    articleUrl  = articleUrl,
    imageUrl    = imageUrl,
    tag         = tag,
    publishedAt = publishedAt,
    modTime     = modTime,
    sourceName  = sourceName,
)

@JvmName("networkListToLocal")
fun List<NetworkNews>.toLocal() = map(NetworkNews::toLocal)

// ─── NetworkNews → News (dùng khi không qua local cache, vd preview) ───
fun NetworkNews.toExternal() = News(
    id          = id,
    kind        = NewsKind.fromServer(kind),
    title       = title,
    summary     = summary,
    articleUrl  = articleUrl,
    imageUrl    = imageUrl,
    tag         = tag,
    publishedAt = publishedAt,
    modTime     = modTime,
    sourceName  = sourceName,
    recommendScore           = recommendScore,
    recommendReason          = recommendReason,
    recommendMatchedKeywords = recommendMatchedKeywords,
)

// ═══════════════════════════════════════════════════════════════
//  RECOMMENDATION MAPPERS (MỚI)
// ═══════════════════════════════════════════════════════════════

/**
 * NetworkNews → LocalNewsRecommendation.
 * Trả null nếu instance không phải news đề xuất (recommendScore == null).
 */
fun NetworkNews.toLocalRecommendation(): LocalNewsRecommendation? {
    val s = recommendScore ?: return null
    return LocalNewsRecommendation(
        newsId             = id,
        score              = s,
        reason             = recommendReason,
        matchedKeywordsCsv = recommendMatchedKeywords.joinToString(","),
        generatedAt        = System.currentTimeMillis(),
        isDismissed        = false,
    )
}

/**
 * LocalNewsWithRecommendation → News (cho stream UI).
 * Tận dụng [LocalNews.toExternal] rồi copy thêm 3 field recommend.
 */
fun LocalNewsWithRecommendation.toExternal(): News = news.toExternal().copy(
    recommendScore           = score,
    recommendReason          = reason,
    recommendMatchedKeywords = if (matchedKeywordsCsv.isBlank()) emptyList()
                               else matchedKeywordsCsv.split(","),
)

@JvmName("localWithRecListToExternal")
fun List<LocalNewsWithRecommendation>.toExternal(): List<News> =
    map(LocalNewsWithRecommendation::toExternal)
