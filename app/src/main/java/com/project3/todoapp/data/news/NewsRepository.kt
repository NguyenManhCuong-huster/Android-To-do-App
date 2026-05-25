package com.project3.todoapp.data.news

import kotlinx.coroutines.flow.Flow

/**
 * NewsRepository — single source of truth cho News trong toàn app.
 *
 * Khác TaskRepository ở chỗ:
 *  - News read-only ở client → không có create/update/delete.
 *  - [refresh] chỉ PULL (không có PUSH).
 *  - [getNewsStream] luôn trigger refresh ngầm khi UI bắt đầu observe.
 *
 * Pattern offline-first vẫn giữ:
 *  - Mọi READ trả về thẳng từ Room (UI có data ngay cả khi offline).
 *  - Network chỉ là best-effort để cập nhật cache.
 */
interface NewsRepository {

    /**
     * Quan sát danh sách news. Có thể lọc theo kind/tag.
     * Tự trigger refresh khi bắt đầu observe (best-effort, không block).
     *
     * @param kind  Lọc theo loại (NEWS hoặc PLAN); null = lấy hết.
     * @param tag   Lọc theo tag (CTSV/ĐTĐH/...); null = lấy hết.
     */
    fun getNewsStream(kind: NewsKind? = null, tag: String? = null): Flow<List<News>>

    /** Search trong title/summary. Tự trigger refresh. */
    fun searchNews(query: String): Flow<List<News>>

    /** Lấy 1 news theo id (chỉ từ local — gọi sau khi đã refresh). */
    suspend fun getNews(id: String): News?

    /**
     * Đồng bộ 1 chiều từ server: kéo toàn bộ news, replace cache local.
     * Dùng [androidx.room.Transaction] nội bộ để UI Flow không thấy
     * trạng thái rỗng tạm thời.
     * No-op nếu offline / chưa đăng nhập.
     */
    suspend fun refresh()

    /** Reset cache local (gọi khi đăng xuất / đổi user). */
    suspend fun deleteAllNews()
}
