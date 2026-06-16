package com.project.hustassistant.data.userinfo.network

import com.project.hustassistant.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * UserInfoApi — endpoint REST quản lý profile sinh viên (HUST).
 *
 * Tạo package mới `data/userinfo/network/`. ProfileViewModel hiện gọi trực tiếp
 * UserInfoApi (chưa có Repository) — vẫn OK, chỉ cần đổi đường dẫn import.
 */

// ─── DTOs ──────────────────────────────────────────────
data class UserInfoDto(
    val user_id: String?,
    val student_id: String?,
    val full_name: String?,
    val date_of_birth: String?, // "YYYY-MM-DD"
    val phone: String?,
    val school: String?,
    val major: String?,
    val class_name: String?,
    val course: String?,
    val created_at: String?,
    val mod_time: String?,
)

data class UpsertUserInfoBody(
    val student_id: String?,
    val full_name: String?,
    val date_of_birth: String?,
    val phone: String?,
    val school: String?,
    val major: String?,
    val class_name: String?,
    val course: String?,
)

// ─── API ───────────────────────────────────────────────
interface UserInfoApi {
    @GET("api/user-info")
    suspend fun get(): ApiResponse<UserInfoDto>

    /** Upsert toàn bộ — tạo mới nếu chưa có, thay thế nếu đã có. */
    @PUT("api/user-info")
    suspend fun upsert(@Body body: UpsertUserInfoBody): ApiResponse<UserInfoDto>

    @DELETE("api/user-info")
    suspend fun delete(): ApiResponse<Unit>
}
