package com.project.hustassistant.data.grade.network

import com.project.hustassistant.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GradeApi — endpoint REST của Kết quả học tập.
 *
 * Đặt cùng package với [NetworkGradeSource], cùng feature data/grade/.
 * Repository [com.project.hustassistant.data.grade.DefaultGradeRepository] không import file này.
 */

// ─── DTOs ──────────────────────────────────────────────
data class GradeDto(
    val id: String,
    val semester: String?,
    val course_code: String?,
    val course_name: String?,
    val course_name_en: String?,
    val credits: Int?,
    val letter_grade: String?,
    val is_deleted: Boolean?,
    val mod_time: String?,
)

data class CreateGradeBody(
    // id do client cấp (UUID ổn định) → server dùng luôn làm PK, KHÔNG cấp id mới.
    // Đây là idempotency key: POST lại cùng id sẽ gộp về 1 dòng, không nhân bản.
    val id: String,
    // mod_time client (ISO) → server quyết LWW (so '>') + lưu đúng mốc này.
    val mod_time: String? = null,
    val semester: String,
    val course_code: String,
    val course_name: String,
    val course_name_en: String? = null,
    val credits: Int = 0,
    val letter_grade: String? = null,
)

data class UpdateGradeBody(
    val mod_time: String? = null,
    val semester: String,
    val course_code: String,
    val course_name: String,
    val course_name_en: String? = null,
    val credits: Int = 0,
    val letter_grade: String? = null,
)

// ─── API ───────────────────────────────────────────────
interface GradeApi {
    @GET("api/grades")
    suspend fun list(
        @Query("include_deleted") includeDeleted: Boolean = true,
    ): ApiResponse<List<GradeDto>>

    @POST("api/grades")
    suspend fun create(@Body body: CreateGradeBody): ApiResponse<GradeDto>

    @PUT("api/grades/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateGradeBody,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<GradeDto>

    @DELETE("api/grades/{id}")
    suspend fun delete(
        @Path("id") id: String,
        @Header("x-client-mod-time") clientModTime: String? = null,
    ): ApiResponse<GradeDto>
}
