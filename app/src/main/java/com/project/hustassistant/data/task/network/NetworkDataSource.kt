package com.project.hustassistant.data.task.network

import com.project.hustassistant.data.task.Task

/**
 * NetworkDataSource — interface chung cho mọi nguồn dữ liệu network của Task.
 *
 *  - [com.project.hustassistant.data.task.DefaultTaskRepository] CHỈ phụ thuộc interface này.
 *  - Implementation thật: [TaskNetworkDataSource] (REST qua [TaskApi]).
 *  - Khi test có thể inject FakeNetworkTaskSource.
 *
 * Quy ước (giống NetworkDataSource của Tag):
 *  - MỌI method PHẢI tự bắt exception, trả về null/false/emptyList khi lỗi.
 *    Lý do: repository chạy local-first, network chỉ là best-effort, không nên ném lỗi lên UI.
 *  - [loadTasks] trả về CẢ task đã soft-delete (is_deleted=true) để repository có thể
 *    đồng bộ tombstone về local. Đây là điểm KHÁC interface cũ — interface cũ filter
 *    sẵn ở data source nên client không thấy được task đã xoá → bug "task hồi sinh".
 */
interface NetworkDataSource {

    /** Lấy toàn bộ task của user (kể cả đã xoá mềm để đồng bộ Last-Write-Wins). */
    suspend fun loadTasks(): List<NetworkTask>

    /** Tạo task mới trên server. Trả về task với id server cấp; null nếu lỗi. */
    suspend fun createTask(task: Task): NetworkTask?

    /** Cập nhật task. Trả về task sau cập nhật; null nếu lỗi/conflict (LWW). */
    suspend fun updateTask(task: Task): NetworkTask?

    /** Xoá mềm task trên server (kèm x-client-mod-time để LWW). Trả về true nếu thành công. */
    suspend fun deleteTask(id: String, clientModTime: Long): Boolean
}
