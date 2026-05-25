package com.project3.todoapp.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * AuthApi — endpoint đăng nhập / đăng xuất / lấy thông tin user hiện tại.
 *
 * Giữ ở `com.project3.todoapp.network` (không tách theo feature) vì auth là một phần
 * của hạ tầng kết nối server: TokenStore + ApiClient + AuthInterceptor cùng chỗ với
 * AuthApi giúp đọc luồng auth gọn trong 1 package.
 */

// ─── DTOs ──────────────────────────────────────────────
data class GoogleMobileLoginBody(
    val id_token: String,
    val server_auth_code: String? = null,
)

data class LoginResult(
    val token: String,
    val user: UserDto,
)

data class UserDto(
    val id: String,
    val email: String,
    val created_at: String?,
)

// ─── API ───────────────────────────────────────────────
interface AuthApi {
    @POST("api/auth/google/mobile")
    suspend fun googleLogin(@Body body: GoogleMobileLoginBody): ApiResponse<LoginResult>

    @GET("api/auth/me")
    suspend fun me(): ApiResponse<UserDto>

    @POST("api/auth/logout")
    suspend fun logout(): ApiResponse<Unit>
}
