package com.project.hustassistant.data.grade

/**
 * Grade — model dùng trong toàn bộ app (UI / ViewModel / domain layer).
 *
 * Một bản ghi = điểm của 1 học phần trong 1 học kỳ.
 *
 * Đây là "external model" — không biết gì về Room hay Retrofit.
 * Dùng [ModelMappingExt.kt] để convert sang [LocalGrade] (Room) hoặc [NetworkGrade] (API).
 *
 * @param semester     mã học kỳ, vd "20251" (2025 kỳ 1).
 * @param courseCode   mã học phần, vd "IT4785".
 * @param courseName   tên học phần tiếng Việt.
 * @param courseNameEn tên học phần tiếng Anh (có thể rỗng).
 * @param credits      số tín chỉ (TC). PE thường = 0 → không tính GPA.
 * @param letterGrade  điểm chữ ("A", "B+"...). Rỗng = chưa có điểm.
 */
data class Grade(
    val id: String,
    val semester: String,
    val courseCode: String,
    val courseName: String,
    val courseNameEn: String,
    val credits: Int,
    val letterGrade: String,
    val modTime: Long,
)
