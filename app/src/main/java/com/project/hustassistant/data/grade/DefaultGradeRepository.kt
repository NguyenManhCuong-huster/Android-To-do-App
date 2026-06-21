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
import kotlinx.coroutines.sync.Mutex
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
 *   - PUSH self-heal: grade local CÒN SỐNG mà server không hề biết (kể cả tombstone) →
 *     re-upload. Cứu desync sau khi DB server bị reset / đổi backend (local clean nên
 *     dirty-only push bỏ sót). Tombstone vẫn được tôn trọng → không hồi sinh môn đã xoá.
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

    // GUARD chống sync chạy CHỒNG NHAU. GradesViewModel có nhiều StateFlow, mỗi cái
    // observe getGradesStream() → trySync() → trước đây nổ ra 4-7 sync SONG SONG, tất
    // cả cùng thấy server rỗng và cùng POST orphan → NHÂN BẢN môn. tryLock gộp các lần
    // trùng: đang có sync chạy thì bỏ qua (dirty flag vẫn còn → lần sync sau lo nốt).
    private val syncMutex = Mutex()

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
        if (!syncMutex.tryLock()) return        // đã có sync đang chạy → gộp, bỏ lần này
        try {
            withContext(dispatcher) {
                pushLocalChanges() // đẩy dirty trước để không bị PULL ghi đè
                pullRemoteChanges()
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun pushLocalChanges() {
        // 1 lần GET, dùng cho cả quyết định create/update LẪN phát hiện orphan.
        // loadGrades() đã include_deleted=true → biết được cả tombstone.
        val remote    = networkDataSource.loadGrades()
        val knownIds  = remote.map { it.id }.toSet()                       // server BIẾT id này (active hoặc đã xoá)
        val activeIds = remote.filter { !it.isDeleted }.map { it.id }.toSet()

        val dirty    = gradeDao.getDirtyGrades()
        val dirtyIds = dirty.map { it.id }.toSet()

        // SELF-HEAL: grade local còn sống mà server CHƯA TỪNG biết (không có cả tombstone)
        // → re-upload. Xảy ra sau khi DB server bị reset / đổi backend. Loại trừ id đã có
        // tombstone để KHÔNG hồi sinh môn người dùng đã cố ý xoá (pull sẽ tự xoá chúng).
        val orphans = gradeDao.getAll()
            .filter { it.id !in knownIds && it.id !in dirtyIds }

        val toPush = dirty + orphans
        if (toPush.isEmpty()) return
        if (orphans.isNotEmpty()) {
            Log.w(TAG, "Self-heal: re-uploading ${orphans.size} grade(s) absent on server")
        }

        for (local in toPush) {
            try {
                pushOne(local, activeIds)
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
                    val ok = networkDataSource.deleteGrade(local.id, local.modTime)
                    if (ok) gradeDao.hardDeleteById(local.id)
                } else {
                    gradeDao.hardDeleteById(local.id)
                }
            }
            // (2) Chưa có trên server → POST kèm local.id. Server dùng LUÔN id này (idempotency
            //     key) nên không còn cảnh "server cấp id khác" → khỏi reassign. POST lại cùng id
            //     (race/self-heal) chỉ gộp về 1 dòng. reassignId vẫn giữ làm lưới an toàn phòng
            //     server (cũ) trả id khác.
            local.id !in remoteIds -> {
                val remote = networkDataSource.createGrade(
                    id           = local.id,
                    semester     = local.semester,
                    courseCode   = local.courseCode,
                    courseName   = local.courseName,
                    courseNameEn = local.courseNameEn,
                    credits      = local.credits,
                    letterGrade  = local.letterGrade,
                    modTime      = local.modTime,
                )
                // Server dùng LUÔN id client → remote.id == local.id, không còn reassign.
                if (remote != null) gradeDao.markSynced(local.id)
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
                    modTime      = local.modTime,
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
