package com.project.hustassistant.data.grade.network

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * NetworkGradeSource — implementation thật của [NetworkDataSource], gọi REST qua [GradeApi].
 *
 * Mọi method bắt exception và trả giá trị "trống" (null/false/emptyList) để caller
 * (repository) không phải try/catch — phù hợp chiến lược local-first best-effort sync.
 *
 * ⚠️ MỌI catch ĐỀU LOG lý do (TAG="GradeNet"): trước đây nuốt im lặng nên khi grade
 * không sync lên server thì không có manh mối nào trong Logcat. Lọc Logcat theo
 * "GradeNet" để thấy chính xác request nào fail và vì sao (401, 400 validate, timeout…).
 */
class NetworkGradeSource(
    private val gradeApi: GradeApi,
) : NetworkDataSource {

    override suspend fun loadGrades(): List<NetworkGrade> = try {
        gradeApi.list(includeDeleted = true).data
            ?.map { it.toNetworkGrade() }
            ?: emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "loadGrades (GET /api/grades) failed", e)
        emptyList()
    }

    override suspend fun createGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
        modTime: Long,
    ): NetworkGrade? = try {
        gradeApi.create(
            CreateGradeBody(
                id             = id,
                mod_time       = toIso(modTime),
                semester       = semester,
                course_code    = courseCode,
                course_name    = courseName,
                course_name_en = courseNameEn.ifBlank { null },
                credits        = credits,
                letter_grade   = letterGrade.ifBlank { null },
            ),
        ).data?.toNetworkGrade()
    } catch (e: Exception) {
        Log.e(TAG, "createGrade (POST /api/grades) failed for $courseCode @ $semester", e)
        null
    }

    override suspend fun updateGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
        modTime: Long,
    ): NetworkGrade? = try {
        gradeApi.update(
            id,
            UpdateGradeBody(
                mod_time       = toIso(modTime),
                semester       = semester,
                course_code    = courseCode,
                course_name    = courseName,
                course_name_en = courseNameEn.ifBlank { null },
                credits        = credits,
                letter_grade   = letterGrade.ifBlank { null },
            ),
            clientModTime = toIso(modTime),
        ).data?.toNetworkGrade()
    } catch (e: Exception) {
        Log.e(TAG, "updateGrade (PUT /api/grades/$id) failed", e)
        null
    }

    override suspend fun deleteGrade(id: String, modTime: Long): Boolean = try {
        gradeApi.delete(id, clientModTime = toIso(modTime)).success
    } catch (e: Exception) {
        Log.e(TAG, "deleteGrade (DELETE /api/grades/$id) failed", e)
        false
    }

    // ─── Helpers ─────────────────────────────────────────
    private fun GradeDto.toNetworkGrade() = NetworkGrade(
        id           = id,
        semester     = semester ?: "",
        courseCode   = course_code ?: "",
        courseName   = course_name ?: "",
        courseNameEn = course_name_en ?: "",
        credits      = credits ?: 0,
        letterGrade  = letter_grade ?: "",
        isDeleted    = is_deleted ?: false,
        modTime      = parseIso(mod_time),
    )

    private companion object {
        private const val TAG = "GradeNet"
    }

    private fun toIso(epochMs: Long): String? =
        if (epochMs <= 0L) null
        else SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(epochMs))

    private fun parseIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
            fmt.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
