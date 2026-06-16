package com.project.hustassistant.data.userinfo

/**
 * UserInfo — model dùng trong UI / ViewModel.
 *
 * Là PROFILE 1-1 với user hiện tại. Vì chỉ có 1 row → không cần id riêng.
 * `email` đến từ /api/auth/me (denormalized vào đây để UI chỉ phải observe 1 stream).
 *
 * `isDirty = true` nghĩa là user vừa edit offline, repository chưa push lên server.
 * UI có thể hiện badge "đang đợi đồng bộ" dựa trên cờ này.
 */
data class UserInfo(
    val email: String?,
    val studentId: String?,
    val fullName: String?,
    val dateOfBirth: String?,    // "YYYY-MM-DD"
    val phone: String?,
    val school: String?,
    val major: String?,
    val className: String?,
    val course: String?,
    val isDirty: Boolean,
    val modTime: Long,
)
