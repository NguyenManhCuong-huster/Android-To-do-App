package com.project3.todoapp.data.tag

import kotlinx.coroutines.flow.Flow

/**
 * TagRepository — single source of truth cho Tag trong toàn app.
 *
 * Quy ước:
 *  - Mọi thao tác READ chỉ trả về tag CHƯA bị xoá (isDeleted=false ở local).
 *  - Mọi thao tác WRITE đều ghi vào local TRƯỚC, sau đó best-effort đẩy lên server.
 *  - UI/ViewModel chỉ làm việc với interface này, không đụng tới Room/Retrofit.
 */
interface TagRepository {

    /** Tạo tag mới. Trả về id (UUID local nếu offline, id server nếu online). */
    suspend fun createTag(name: String, colorHex: String): String

    /** Cập nhật tên/màu của tag. */
    suspend fun updateTag(tagId: String, name: String, colorHex: String)

    /** Xoá mềm tag (đánh dấu isDeleted, sync sẽ push delete lên server). */
    suspend fun deleteTag(tagId: String)

    /** Lấy 1 tag theo id (chỉ trả về nếu chưa bị xoá). */
    suspend fun getTag(tagId: String): Tag?

    /** Quan sát danh sách tag (tự động cập nhật khi local đổi). */
    fun getTagsStream(): Flow<List<Tag>>

    /** Lấy danh sách tag 1 lần. */
    suspend fun getTags(): List<Tag>

    /** Lấy tag của 1 task cụ thể. */
    suspend fun getTagsByTask(taskId: String): List<Tag>

    /** Reset toàn bộ cache local (gọi khi đăng xuất / đổi user). */
    suspend fun deleteAllTags()

    /**
     * Đồng bộ 2 chiều với server:
     *  1) PUSH: đẩy mọi thay đổi local (isDirty=true) lên server.
     *  2) PULL: kéo toàn bộ tag từ server, merge Last-Write-Wins theo modTime.
     * Không làm gì nếu offline hoặc chưa đăng nhập.
     */
    suspend fun sync()
}
