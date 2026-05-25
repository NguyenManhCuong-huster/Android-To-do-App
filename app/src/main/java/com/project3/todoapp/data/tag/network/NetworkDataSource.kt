package com.project3.todoapp.data.tag.network

/**
 * NetworkDataSource — interface chung cho mọi nguồn dữ liệu network của Tag.
 *
 *  - [com.project3.todoapp.data.tag.DefaultTagRepository] CHỈ phụ thuộc vào interface này,
 *    không biết Retrofit hay endpoint cụ thể.
 *  - Implementation thật: [NetworkTagSource] — gọi REST API qua Retrofit.
 *  - Khi test có thể inject FakeNetworkTagSource.
 *
 * Quy ước:
 *  - MỌI method PHẢI tự bắt exception, trả về null/false/emptyList khi lỗi.
 *    Lý do: repository chạy local-first, network chỉ là best-effort, không nên ném lỗi lên UI.
 */
interface NetworkDataSource {

    /** Lấy toàn bộ tag của user (kể cả đã xoá mềm để đồng bộ Last-Write-Wins). */
    suspend fun loadTags(): List<NetworkTag>

    /** Tạo tag mới trên server. Trả về tag với id server cấp; null nếu lỗi. */
    suspend fun createTag(name: String, colorHex: String): NetworkTag?

    /** Cập nhật tag. Trả về tag sau cập nhật; null nếu lỗi. */
    suspend fun updateTag(id: String, name: String, colorHex: String): NetworkTag?

    /** Xoá mềm tag trên server. Trả về true nếu thành công. */
    suspend fun deleteTag(id: String): Boolean
}
