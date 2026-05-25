package com.project3.todoapp.data.account.network

import com.project3.todoapp.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * AccountApi — endpoint REST quản lý liên kết tài khoản (Gmail, Outlook, ...).
 *
 * Tạo package mới `data/account/network/` cho thống nhất với các feature khác.
 * Sau này có thể thêm Repository / Local cache nếu cần.
 */

// ─── DTOs ──────────────────────────────────────────────
data class LinkedAccountDto(
    val id: String,
    val provider: String,
    val username_or_email: String,
    val status: String,
    val linked_at: String?,
)

data class LinkInitBody(val provider: String)

data class LinkInitResult(
    val link_code: String,
    val expires_in: Int,
    val redirect_url: String,
)

// ─── API ───────────────────────────────────────────────
interface AccountApi {
    @GET("api/accounts")
    suspend fun list(): ApiResponse<List<LinkedAccountDto>>

    @POST("api/accounts/link/init")
    suspend fun linkInit(@Body body: LinkInitBody): ApiResponse<LinkInitResult>

    @DELETE("api/accounts/{id}")
    suspend fun unlink(@Path("id") id: String): ApiResponse<Unit>
}
