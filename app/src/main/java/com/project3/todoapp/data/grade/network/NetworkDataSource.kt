package com.project3.todoapp.data.grade.network

/**
 * NetworkDataSource — interface chung cho mọi nguồn dữ liệu network của Grade.
 *
 *  - [com.project3.todoapp.data.grade.DefaultGradeRepository] CHỈ phụ thuộc interface này.
 *  - Implementation thật: [NetworkGradeSource] — gọi REST API qua Retrofit.
 *
 * Quy ước:
 *  - MỌI method PHẢI tự bắt exception, trả null/false/emptyList khi lỗi
 *    (repository chạy local-first, network chỉ là best-effort).
 */
interface NetworkDataSource {

    /** Lấy toàn bộ grade của user (kể cả đã xoá mềm để LWW). */
    suspend fun loadGrades(): List<NetworkGrade>

    /** Tạo grade mới trên server. Trả về grade với id server cấp; null nếu lỗi. */
    suspend fun createGrade(
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ): NetworkGrade?

    /** Cập nhật grade. Trả về grade sau cập nhật; null nếu lỗi. */
    suspend fun updateGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ): NetworkGrade?

    /** Xoá mềm grade trên server. Trả về true nếu thành công. */
    suspend fun deleteGrade(id: String): Boolean
}
