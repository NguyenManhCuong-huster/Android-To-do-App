package com.project3.todoapp.data.grade.network

import com.project3.todoapp.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GradeApi — endpoint REST của Kết quả học tập.
 *
 * Đặt cùng package với [NetworkGradeSource], cùng feature data/grade/.
 * Repository [com.project3.todoapp.data.grade.DefaultGradeRepository] không import file này.
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
    val semester: String,
    val course_code: String,
    val course_name: String,
    val course_name_en: String? = null,
    val credits: Int = 0,
    val letter_grade: String? = null,
)

data class UpdateGradeBody(
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
    ): ApiResponse<GradeDto>

    @DELETE("api/grades/{id}")
    suspend fun delete(@Path("id") id: String): ApiResponse<GradeDto>
}
