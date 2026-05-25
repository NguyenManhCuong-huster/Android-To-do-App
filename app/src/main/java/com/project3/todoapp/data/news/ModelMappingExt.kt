package com.project3.todoapp.data.news

import com.project3.todoapp.data.news.local.LocalNews
import com.project3.todoapp.data.news.network.NetworkNews

/*
 * ModelMappingExt — chuyển đổi giữa 3 model:
 *
 *      LocalNews (Room) ──► News (external) ◄── NetworkNews (API)
 *
 * Khác Task: News chỉ có 1 chiều LOCAL → EXTERNAL và NETWORK → LOCAL
 *  - Không có Task → Local (vì client không tạo News)
 *  - Không có Local → Network (vì client không push News)
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
)
