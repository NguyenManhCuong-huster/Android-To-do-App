package com.project.hustassistant.data.userinfo.network

import com.project.hustassistant.network.AuthApi
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * NetworkUserInfo.kt
 *
 *   • NetworkUserInfo  — model trung gian, repository không phải biết DTO Retrofit.
 *   • NetworkDataSource — abstract khỏi Retrofit để dễ test (mock).
 *   • UserInfoRemoteDataSource — implementation thật, gọi UserInfoApi + AuthApi.
 *
 * Vì UserInfo là 1-1 với user, NetworkDataSource cũng chỉ cần load/save 1 cái.
 * `loadCurrent()` gộp /me và /user-info để repository chỉ cần 1 lần network round trip
 * cho thành phần UI cần (email + profile fields).
 */

// ─── Domain trung gian ─────────────────────────────────
data class NetworkUserInfo(
    val email: String?,
    val studentId: String?,
    val fullName: String?,
    val dateOfBirth: String?,
    val phone: String?,
    val school: String?,
    val major: String?,
    val className: String?,
    val course: String?,
    val modTime: Long,
)

// ─── Interface ─────────────────────────────────────────
interface NetworkDataSource {
    /** Pull cả email (/me) và profile (/user-info) → ghép. */
    suspend fun loadCurrent(): NetworkUserInfo?

    /** Push profile fields lên server. Trả về bản server-confirmed. */
    suspend fun saveCurrent(payload: NetworkUserInfo): NetworkUserInfo?
}

// ─── Impl ──────────────────────────────────────────────
class UserInfoRemoteDataSource(
    private val userInfoApi: UserInfoApi,
    private val authApi: AuthApi,
) : NetworkDataSource {

    override suspend fun loadCurrent(): NetworkUserInfo? {
        // Lấy email từ /me. Nếu /me lỗi (vd 401) — coi như chưa login, trả null.
        val email = try {
            authApi.me().data?.email
        } catch (_: Exception) {
            return null
        }

        // /user-info có thể trả 404 lần đầu (chưa từng tạo) — không coi là lỗi.
        val info = try {
            userInfoApi.get().data
        } catch (_: Exception) {
            null
        }

        return NetworkUserInfo(
            email = email,
            studentId = info?.student_id,
            fullName = info?.full_name,
            dateOfBirth = info?.date_of_birth,
            phone = info?.phone,
            school = info?.school,
            major = info?.major,
            className = info?.class_name,
            course = info?.course,
            modTime = parseIso(info?.mod_time),
        )
    }

    override suspend fun saveCurrent(payload: NetworkUserInfo): NetworkUserInfo? {
        val body = UpsertUserInfoBody(
            student_id = payload.studentId,
            full_name = payload.fullName,
            date_of_birth = payload.dateOfBirth,
            phone = payload.phone,
            school = payload.school,
            major = payload.major,
            class_name = payload.className,
            course = payload.course,
        )
        val saved = try {
            userInfoApi.upsert(body).data
        } catch (_: Exception) {
            return null
        }
        return NetworkUserInfo(
            email = payload.email, // server không trả email ở endpoint này → giữ nguyên
            studentId = saved?.student_id,
            fullName = saved?.full_name,
            dateOfBirth = saved?.date_of_birth,
            phone = saved?.phone,
            school = saved?.school,
            major = saved?.major,
            className = saved?.class_name,
            course = saved?.course,
            modTime = parseIso(saved?.mod_time),
        )
    }

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
