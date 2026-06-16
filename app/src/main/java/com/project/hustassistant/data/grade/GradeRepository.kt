package com.project.hustassistant.data.grade

import kotlinx.coroutines.flow.Flow

/**
 * GradeRepository — single source of truth cho Kết quả học tập (Grade) trong app.
 *
 * Quy ước (giống TagRepository):
 *  - READ chỉ trả về grade CHƯA bị xoá (isDeleted=false ở local).
 *  - WRITE ghi vào local TRƯỚC (instant + offline-friendly), sau đó best-effort push lên server.
 *  - UI/ViewModel chỉ làm việc với interface này, không đụng tới Room/Retrofit.
 */
interface GradeRepository {

    /** Tạo điểm học phần mới. Trả về id (UUID local nếu offline, id server nếu online). */
    suspend fun createGrade(
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ): String

    /** Cập nhật điểm học phần. */
    suspend fun updateGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    )

    /** Xoá mềm (đánh dấu isDeleted, sync sẽ push delete lên server). */
    suspend fun deleteGrade(id: String)

    /** Lấy 1 grade theo id (chỉ trả về nếu chưa bị xoá). */
    suspend fun getGrade(id: String): Grade?

    /** Quan sát toàn bộ grade chưa xoá (tự cập nhật khi local đổi). */
    fun getGradesStream(): Flow<List<Grade>>

    /** Lấy danh sách grade 1 lần. */
    suspend fun getGrades(): List<Grade>

    /** Reset toàn bộ cache local (gọi khi đăng xuất / đổi user). */
    suspend fun deleteAllGrades()

    /**
     * Đồng bộ 2 chiều với server:
     *  1) PUSH: đẩy mọi thay đổi local (isDirty=true) lên server.
     *  2) PULL: kéo toàn bộ grade từ server, merge Last-Write-Wins theo modTime.
     * Không làm gì nếu offline hoặc chưa đăng nhập.
     */
    suspend fun sync()
}
