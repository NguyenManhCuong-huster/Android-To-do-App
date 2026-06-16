package com.project.hustassistant.data.grade.network

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * NetworkGradeSource — implementation thật của [NetworkDataSource], gọi REST qua [GradeApi].
 *
 * Mọi method bắt exception và trả giá trị "trống" (null/false/emptyList) để caller
 * (repository) không phải try/catch — phù hợp chiến lược local-first best-effort sync.
 */
class NetworkGradeSource(
    private val gradeApi: GradeApi,
) : NetworkDataSource {

    override suspend fun loadGrades(): List<NetworkGrade> = try {
        gradeApi.list(includeDeleted = true).data
            ?.map { it.toNetworkGrade() }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun createGrade(
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ): NetworkGrade? = try {
        gradeApi.create(
            CreateGradeBody(
                semester       = semester,
                course_code    = courseCode,
                course_name    = courseName,
                course_name_en = courseNameEn.ifBlank { null },
                credits        = credits,
                letter_grade   = letterGrade.ifBlank { null },
            ),
        ).data?.toNetworkGrade()
    } catch (_: Exception) {
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
    ): NetworkGrade? = try {
        gradeApi.update(
            id,
            UpdateGradeBody(
                semester       = semester,
                course_code    = courseCode,
                course_name    = courseName,
                course_name_en = courseNameEn.ifBlank { null },
                credits        = credits,
                letter_grade   = letterGrade.ifBlank { null },
            ),
        ).data?.toNetworkGrade()
    } catch (_: Exception) {
        null
    }

    override suspend fun deleteGrade(id: String): Boolean = try {
        gradeApi.delete(id).success
    } catch (_: Exception) {
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
