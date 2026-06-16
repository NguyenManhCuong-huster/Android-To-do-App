package com.project.hustassistant.data.grade.network

/**
 * NetworkGrade — DTO trung gian giữa repository và Retrofit layer.
 *
 * Tách khỏi [GradeDto] (raw JSON server) để repository không phụ thuộc tên field API,
 * dễ test bằng fake data source.
 */
data class NetworkGrade(
    val id: String,
    val semester: String,
    val courseCode: String,
    val courseName: String,
    val courseNameEn: String,
    val credits: Int,
    val letterGrade: String,
    val isDeleted: Boolean,
    val modTime: Long, // epoch millis
)
