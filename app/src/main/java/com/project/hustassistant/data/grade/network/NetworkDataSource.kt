package com.project.hustassistant.data.grade.network

/**
 * NetworkDataSource — interface chung cho mọi nguồn dữ liệu network của Grade.
 *
 *  - [com.project.hustassistant.data.grade.DefaultGradeRepository] CHỈ phụ thuộc interface này.
 *  - Implementation thật: [NetworkGradeSource] — gọi REST API qua Retrofit.
 *
 * Quy ước:
 *  - MỌI method PHẢI tự bắt exception, trả null/false/emptyList khi lỗi
 *    (repository chạy local-first, network chỉ là best-effort).
 */
interface NetworkDataSource {

    /** Lấy toàn bộ grade của user (kể cả đã xoá mềm để LWW). */
    suspend fun loadGrades(): List<NetworkGrade>

    /**
     * Tạo grade trên server. [id] = UUID client tự sinh, server dùng luôn làm PK (idempotency
     * key chống nhân bản khi POST lại). Trả về grade server (id == [id] gửi lên); null nếu lỗi.
     */
    suspend fun createGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
        modTime: Long,
    ): NetworkGrade?

    /** Cập nhật grade. [modTime] = thời điểm sửa client để server LWW. Trả về grade sau cập nhật; null nếu lỗi. */
    suspend fun updateGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
        modTime: Long,
    ): NetworkGrade?

    /** Xoá mềm grade trên server ([modTime] để server LWW). Trả về true nếu thành công. */
    suspend fun deleteGrade(id: String, modTime: Long): Boolean
}
