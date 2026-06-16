package com.project.hustassistant.data.userinfo

import android.util.Log
import com.project.hustassistant.authentication.AuthManager
import com.project.hustassistant.data.userinfo.local.LocalUserInfo
import com.project.hustassistant.data.userinfo.local.UserInfoDAO
import com.project.hustassistant.data.userinfo.network.NetworkDataSource
import com.project.hustassistant.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DefaultUserInfoRepository — local-first + offline edit.
 *
 * ════════════════════════════════════════════════════════════
 *  CHIẾN LƯỢC SYNC
 * ════════════════════════════════════════════════════════════
 *
 *  save() (vd user bấm "Lưu"):
 *      1. Ghi vào Room với isDirty = true, modTime = now.
 *      2. UI cập nhật ngay qua Flow.
 *      3. Background: nếu online → trySync() (sẽ push + pull).
 *
 *  sync() (vd user pull-to-refresh, hoặc app khởi động):
 *      Bước 1 — PUSH:
 *          - Nếu local có row VÀ isDirty = true → POST lên server.
 *          - Server trả về modTime mới → markSynced().
 *          - Push fail (offline / server error) → giữ nguyên dirty,
 *            lần sync sau thử lại. KHÔNG continue sang pull (tránh
 *            overwrite local edit chưa push).
 *
 *      Bước 2 — PULL:
 *          - GET /me + /user-info → ghép thành NetworkUserInfo.
 *          - Last-Write-Wins theo modTime: chỉ overwrite local nếu
 *            remote.modTime ≥ local.modTime. (Bằng = upsert dữ liệu mới
 *            kể cả modTime trùng — vd email mới đổi.)
 *          - KHÔNG ghi đè nếu local đang dirty (đã handle ở step 1 — nếu
 *            push fail thì step 2 không nên xoá edit của user).
 */
class DefaultUserInfoRepository(
    private val userInfoDao: UserInfoDAO,
    private val networkDataSource: NetworkDataSource,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserInfoRepository {

    private fun shouldSync(): Boolean =
        networkManager.isOnline() && authManager.isUserLoggedIn()

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════
    override fun observe(): Flow<UserInfo?> =
        userInfoDao.observe().map { it?.toExternal() }

    // ═════════════════════════════════════════════════════
    //  WRITE — local-first
    // ═════════════════════════════════════════════════════
    override suspend fun save(
        studentId: String?,
        fullName: String?,
        dateOfBirth: String?,
        phone: String?,
        school: String?,
        major: String?,
        className: String?,
        course: String?,
    ) = withContext(dispatcher) {
        val current = userInfoDao.get()
        val updated = LocalUserInfo(
            id = LocalUserInfo.SINGLETON_ID,
            email = current?.email,            // giữ email hiện tại (read-only từ user)
            studentId = studentId?.ifBlank { null },
            fullName = fullName?.ifBlank { null },
            dateOfBirth = dateOfBirth?.ifBlank { null },
            phone = phone?.ifBlank { null },
            school = school?.ifBlank { null },
            major = major?.ifBlank { null },
            className = className?.ifBlank { null },
            course = course?.ifBlank { null },
            isDirty = true,
            modTime = System.currentTimeMillis(),
        )
        userInfoDao.upsert(updated)
        trySync()
    }

    // ═════════════════════════════════════════════════════
    //  SYNC
    // ═════════════════════════════════════════════════════
    override suspend fun sync(): Result<Unit> = runCatching {
        if (!shouldSync()) {
            Log.d(TAG, "sync skipped: offline or not logged-in")
            return@runCatching
        }
        withContext(dispatcher) {
            // 1) PUSH dirty (nếu có)
            val pushOk = pushIfDirty()

            // 2) PULL — chỉ khi push không lỗi (hoặc không có gì cần push)
            if (pushOk) pull()
        }
    }

    /**
     * Trả về true nếu (a) không có gì cần push, hoặc (b) push thành công.
     * Trả false nếu có dirty mà push fail → caller skip pull để bảo toàn local.
     */
    private suspend fun pushIfDirty(): Boolean {
        val local = userInfoDao.get() ?: return true   // chưa có row → không push
        if (!local.isDirty) return true

        val saved = networkDataSource.saveCurrent(local.toNetwork())
        return if (saved != null) {
            // Server đã nhận. Cập nhật modTime server-confirmed + clear dirty.
            // Dùng upsert thay vì markSynced để cũng ghi nhận field mới (vd
            // server normalize input — đỡ phải fetch lại).
            userInfoDao.upsert(saved.toLocal(isDirty = false))
            true
        } else {
            Log.w(TAG, "push failed — keep dirty, skip pull")
            false
        }
    }

    private suspend fun pull() {
        val remote = networkDataSource.loadCurrent() ?: return
        val local = userInfoDao.get()

        // LWW theo modTime. Nếu local null (chưa từng load) — luôn ghi.
        val shouldOverwrite =
            local == null ||
                (!local.isDirty && remote.modTime >= local.modTime) ||
                // Trường hợp đặc biệt: profile chưa từng tạo (modTime = 0 từ /user-info 404)
                // → vẫn lưu để giữ email từ /me.
                (local.modTime == 0L)

        if (shouldOverwrite) {
            userInfoDao.upsert(remote.toLocal(isDirty = false))
        }
    }

    /** Background sync — bắn-và-quên, nuốt mọi exception. */
    private fun trySync() {
        if (!shouldSync()) return
        scope.launch {
            runCatching { sync() }.onFailure { Log.w(TAG, "trySync failed", it) }
        }
    }

    override suspend fun clearLocal() = withContext(dispatcher) {
        userInfoDao.clear()
    }

    companion object {
        private const val TAG = "UserInfoRepo"
    }
}
