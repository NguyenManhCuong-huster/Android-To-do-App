package com.project3.todoapp.data.task

import kotlinx.coroutines.flow.Flow

/**
 * TaskRepository — single source of truth cho Task trong toàn app.
 *
 * Quy ước (giống TagRepository):
 *  - Mọi thao tác READ chỉ trả về task CHƯA bị xoá (isDeleted=false ở local).
 *  - Mọi thao tác WRITE đều ghi vào local TRƯỚC, sau đó best-effort đẩy lên server.
 *  - UI/ViewModel chỉ làm việc với interface này, không đụng tới Room/Retrofit.
 */
interface TaskRepository {

    /** Tạo task mới. Trả về id (UUID local nếu offline, sẽ được đổi sang id server khi sync). */
    suspend fun createTask(
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority = Priority.MEDIUM,
        addressName: String? = null,
    ): String

    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority,
        addressName: String?,
    )

    suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean)

    /** Lấy 1 task theo id (chỉ trả về nếu chưa bị xoá). */
    suspend fun getTask(taskId: String): Task?

    /** Xoá mềm task (đánh dấu isDeleted, sync sẽ push DELETE lên server). */
    suspend fun deleteTask(taskId: String)

    /** Quan sát danh sách task (tự động cập nhật khi local đổi). */
    fun getTasksStream(): Flow<List<Task>>

    /** Lấy danh sách task 1 lần. */
    suspend fun getTasks(): List<Task>

    /** Quan sát task theo tag. */
    fun getTasksByTagStream(tagId: String): Flow<List<Task>>

    /** Reset toàn bộ cache local (gọi khi đăng xuất / đổi user). KHÔNG đụng server. */
    suspend fun deleteAllTasks()

    /**
     * Đồng bộ 2 chiều với server:
     *  1) PUSH: đẩy mọi thay đổi local (isDirty=true) lên server.
     *  2) PULL: kéo toàn bộ task từ server, merge Last-Write-Wins theo modTime.
     * Không làm gì nếu offline hoặc chưa đăng nhập.
     */
    suspend fun sync()
}
