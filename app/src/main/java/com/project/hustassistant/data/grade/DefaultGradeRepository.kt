package com.project.hustassistant.data.grade

import android.util.Log
import com.project.hustassistant.authentication.AuthManager
import com.project.hustassistant.data.grade.local.GradeDAO
import com.project.hustassistant.data.grade.local.LocalGrade
import com.project.hustassistant.data.grade.network.NetworkDataSource
import com.project.hustassistant.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * DefaultGradeRepository — local-first, network khi có thể.
 *
 * Triết lý GIỐNG HỆT [com.project.hustassistant.data.tag.DefaultTagRepository]:
 *
 * Mọi WRITE:
 *   1) Cập nhật Room NGAY (UI thấy đổi tức thì, chạy được offline).
 *   2) Đặt isDirty=true trên LocalGrade.
 *   3) Fire-and-forget [trySync] — online + đã login thì push/pull.
 *
 * Mọi READ: lấy thẳng từ Room.
 *
 * SYNC ([sync]):
 *   - PUSH dirty: isDeleted → DELETE; id chưa có ở server → POST (đổi id local theo
 *     server cấp); id đã có → PUT.
 *   - PULL: GET /api/grades?include_deleted=true → upsert local theo Last-Write-Wins
 *     (so sánh modTime).
 *
 *  ⚠️ NOTE về ID: server không nhận client-supplied id ở POST. Grade tạo offline dùng
 *  UUID v4 client; lúc push server cấp id khác → đổi id local qua [GradeDAO.reassignId].
 *  Grade KHÔNG có bảng cross-ref nào tham chiếu tới → reassign đơn giản, không cần FK cascade.
 */
class DefaultGradeRepository(
    private val gradeDao: GradeDAO,
    private val networkDataSource: NetworkDataSource,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GradeRepository {

    // ═════════════════════════════════════════════════════
    //  WRITE
    // ═════════════════════════════════════════════════════

    override suspend fun createGrade(
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ): String = withContext(dispatcher) {
        val id = UUID.randomUUID().toString()
        val grade = Grade(
            id           = id,
            semester     = semester,
            courseCode   = courseCode,
            courseName   = courseName,
            courseNameEn = courseNameEn,
            credits      = credits,
            letterGrade  = letterGrade,
            modTime      = System.currentTimeMillis(),
        )
        gradeDao.upsert(grade.toLocal(isDirty = true, isDeleted = false))
        trySync()
        id
    }

    override suspend fun updateGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ) = withContext(dispatcher) {
        val grade = Grade(
            id           = id,
            semester     = semester,
            courseCode   = courseCode,
            courseName   = courseName,
            courseNameEn = courseNameEn,
            credits      = credits,
            letterGrade  = letterGrade,
            modTime      = System.currentTimeMillis(),
        )
        gradeDao.upsert(grade.toLocal(isDirty = true, isDeleted = false))
        trySync()
    }

    override suspend fun deleteGrade(id: String) = withContext(dispatcher) {
        gradeDao.softDelete(id, System.currentTimeMillis())
        trySync()
    }

    override suspend fun deleteAllGrades() = withContext(dispatcher) {
        gradeDao.deleteAll()
    }

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════

    override suspend fun getGrade(id: String): Grade? = withContext(dispatcher) {
        gradeDao.getById(id)?.takeIf { !it.isDeleted }?.toExternal()
    }

    override fun getGradesStream(): Flow<List<Grade>> {
        trySync() // refresh ngầm khi UI bắt đầu observe
        return gradeDao.observeAll().map { it.toExternal() }
    }

    override suspend fun getGrades(): List<Grade> = withContext(dispatcher) {
        gradeDao.getAll().toExternal()
    }

    // ═════════════════════════════════════════════════════
    //  SYNC
    // ═════════════════════════════════════════════════════

    private fun shouldSync(): Boolean =
        networkManager.isOnline() && authManager.isUserLoggedIn()

    private fun trySync() {
        if (!shouldSync()) return
        scope.launch {
            try {
                sync()
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed", e)
            }
        }
    }

    override suspend fun sync() {
        if (!shouldSync()) return
        withContext(dispatcher) {
            pushLocalChanges() // đẩy dirty trước để không bị PULL ghi đè
            pullRemoteChanges()
        }
    }

    private suspend fun pushLocalChanges() {
        val dirty = gradeDao.getDirtyGrades()
        if (dirty.isEmpty()) return

        val remoteIds: Set<String> = networkDataSource.loadGrades()
            .filter { !it.isDeleted }
            .map { it.id }
            .toSet()

        for (local in dirty) {
            try {
                pushOne(local, remoteIds)
            } catch (e: Exception) {
                Log.e(TAG, "Push grade ${local.id} failed; retry next sync", e)
            }
        }
    }

    private suspend fun pushOne(local: LocalGrade, remoteIds: Set<String>) {
        when {
            // (1) Đã xoá local
            local.isDeleted -> {
                if (local.id in remoteIds) {
                    val ok = networkDataSource.deleteGrade(local.id)
                    if (ok) gradeDao.hardDeleteById(local.id)
                } else {
                    gradeDao.hardDeleteById(local.id)
                }
            }
            // (2) Tạo offline → POST, đổi id theo server cấp
            local.id !in remoteIds -> {
                val remote = networkDataSource.createGrade(
                    semester     = local.semester,
                    courseCode   = local.courseCode,
                    courseName   = local.courseName,
                    courseNameEn = local.courseNameEn,
                    credits      = local.credits,
                    letterGrade  = local.letterGrade,
                )
                when {
                    remote == null -> Unit
                    remote.id != local.id -> gradeDao.reassignId(local.id, remote.id)
                    else -> gradeDao.markSynced(local.id)
                }
            }
            // (3) Đã có trên server → PUT
            else -> {
                val remote = networkDataSource.updateGrade(
                    id           = local.id,
                    semester     = local.semester,
                    courseCode   = local.courseCode,
                    courseName   = local.courseName,
                    courseNameEn = local.courseNameEn,
                    credits      = local.credits,
                    letterGrade  = local.letterGrade,
                )
                if (remote != null) gradeDao.markSynced(local.id)
            }
        }
    }

    private suspend fun pullRemoteChanges() {
        val remoteGrades = networkDataSource.loadGrades()
        if (remoteGrades.isEmpty()) return

        for (remote in remoteGrades) {
            val local = gradeDao.getById(remote.id)
            when {
                remote.isDeleted -> {
                    if (local != null) gradeDao.hardDeleteById(remote.id)
                }
                local == null -> gradeDao.upsert(remote.toLocal(isDirty = false))
                remote.modTime > local.modTime -> gradeDao.upsert(remote.toLocal(isDirty = false))
                // else: giữ local (local mới hơn hoặc đang dirty chờ push lại)
            }
        }
    }

    companion object {
        private const val TAG = "GradeRepo"
    }
}
