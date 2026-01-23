package com.project3.todoapp.data.tag.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDAO {

    // Quan sát danh sách nhãn (Tự động cập nhật UI khi có thay đổi)
    @Query("SELECT * FROM tag ORDER BY tagName ASC")
    fun observeAll(): Flow<List<LocalTag>>

    // Lấy tất cả danh sách nhãn một lần (Dùng cho các logic kiểm tra)
    @Query("SELECT * FROM tag")
    suspend fun getAll(): List<LocalTag>

    // Lấy danh sách các Tag của một Task cụ thể
    @Query(
        """
        SELECT tag.* FROM tag 
        INNER JOIN task_tag_cross_ref ON tag.id = task_tag_cross_ref.tagId 
        WHERE task_tag_cross_ref.taskId = :taskId
    """
    )
    suspend fun getTagsForTask(taskId: String): List<LocalTag>

    // Lấy thông tin chi tiết của một nhãn
    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun getById(id: String): LocalTag?

    // Thêm mới hoặc cập nhật nhãn
    // Nếu ID đã tồn tại, Room sẽ ghi đè (Update), nếu chưa có sẽ thêm mới (Insert)
    @Upsert
    suspend fun upsert(tag: LocalTag)

    // Xóa nhãn theo ID
    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun deleteById(id: String)

    // Xóa toàn bộ nhãn
    @Query("DELETE FROM tag")
    suspend fun deleteAll()

    // Kiểm tra xem một tên nhãn đã tồn tại chưa (Tránh trùng tên)
    @Query("SELECT EXISTS(SELECT 1 FROM tag WHERE tagName = :name LIMIT 1)")
    suspend fun isTagNameExists(name: String): Boolean
}