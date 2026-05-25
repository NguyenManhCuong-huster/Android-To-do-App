package com.project3.todoapp.network

/**
 * ApiCommon — kiểu wrapper dùng chung cho MỌI response của server.
 *
 * Giữ ở đây (không tách theo feature) vì:
 *  - Tất cả endpoint của server đều bọc body trong `{ success, message, data }`.
 *  - Mọi *Api ở data/<feature>/network sẽ import 2 type này.
 */

/** Response envelope cho mọi endpoint trả 1 object. */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?,
)

/** Meta cho response phân trang. */
data class PagedMeta(
    val total: Int,
    val page: Int,
    val limit: Int,
    val total_page: Int,
    val has_next: Boolean,
)

/** Response envelope cho endpoint trả list có phân trang (vd /api/tasks, /api/emails). */
data class PagedResponse<T>(
    val success: Boolean,
    val data: List<T>,
    val meta: PagedMeta,
)
