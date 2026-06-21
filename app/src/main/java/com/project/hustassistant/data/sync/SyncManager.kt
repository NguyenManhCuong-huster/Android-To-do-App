package com.project.hustassistant.data.sync

import android.content.Context
import android.util.Log
import com.project.hustassistant.authentication.AuthManager
import com.project.hustassistant.data.tag.local.LocalTag
import com.project.hustassistant.data.tag.local.TagDAO
import com.project.hustassistant.data.task.local.LocalTask
import com.project.hustassistant.data.task.local.TaskDAO
import com.project.hustassistant.data.tasktag.local.LocalTaskTagCrossRef
import com.project.hustassistant.data.tasktag.local.TaskTagCrossRefDAO
import com.project.hustassistant.network.NetworkManager
import com.project.hustassistant.network.SyncApi
import com.project.hustassistant.network.SyncPushBody
import com.project.hustassistant.network.SyncTagPull
import com.project.hustassistant.network.SyncTagPush
import com.project.hustassistant.network.SyncTaskPull
import com.project.hustassistant.network.SyncTaskPush
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * SyncManager — đồng bộ BATCH 2 chiều cho Task + Tag (+ cross-ref qua tag_ids) qua
 * /api/sync. Là điểm điều phối DUY NHẤT (thay cho push/pull rời rạc trong từng repo)
 * nên thứ tự tags→tasks và tính nguyên tử do server lo trong 1 transaction.
 *
 * Vòng đời 1 lần [sync]:
 *  1) PUSH: gom mọi bản ghi dirty (Task kèm tag_ids hiện tại, Tag) → POST /api/sync.
 *     Server áp client-id + LWW (mod_time client) + "xóa tối thượng". Push xong:
 *     bản đã xoá local → hard-delete; còn lại → markSynced (kể cả "stale" — pull sẽ
 *     mang bản server mới hơn về).
 *  2) PULL: GET /api/sync?since=last_sync_time → áp vào Room với GUARD dirty (không
 *     đè bản local đang chờ push). Cập nhật last_sync_time = server_time.
 *
 * Id ỔN ĐỊNH: client sinh id, server giữ nguyên → KHÔNG còn reassignId.
 */
class SyncManager(
    private val syncApi: SyncApi,
    private val taskDao: TaskDAO,
    private val tagDao: TagDAO,
    private val crossRefDao: TaskTagCrossRefDAO,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val syncMutex = Mutex()

    private fun shouldSync(): Boolean =
        networkManager.isOnline() && authManager.isUserLoggedIn()

    /** Đồng bộ 1 lượt (push rồi pull). Gộp nếu đang có lượt khác chạy. */
    suspend fun sync() {
        if (!shouldSync()) return
        if (!syncMutex.tryLock()) return
        try {
            withContext(dispatcher) {
                push()
                pull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
        } finally {
            syncMutex.unlock()
        }
    }

    /** Reset mốc đồng bộ (gọi khi đăng xuất / đổi user). */
    fun reset() = prefs.edit().remove(KEY_LAST_SYNC).apply()

    // ── PUSH ──────────────────────────────────────────────
    private suspend fun push() {
        val dirtyTags  = tagDao.getDirtyTags()
        val dirtyTasks = taskDao.getDirtyTasks()
        if (dirtyTags.isEmpty() && dirtyTasks.isEmpty()) return

        val tagPush = dirtyTags.map { t ->
            SyncTagPush(
                id = t.id, name = t.tagName, color_hex = t.colorHex,
                is_deleted = t.isDeleted, mod_time = toIso(t.modTime) ?: EPOCH_ZERO,
            )
        }
        val taskPush = dirtyTasks.map { tk ->
            val tagIds = tagDao.getTagsForTask(tk.id).map { it.id } // tag đang gắn (active)
            SyncTaskPush(
                id = tk.id, title = tk.title,
                description = tk.description.ifBlank { null },
                start_time = toIso(tk.start), end_time = toIso(tk.end),
                task_type = "TODO", is_completed = tk.isCompleted, source_type = "MANUAL",
                priority = tk.priority, latitude = tk.latitude, longitude = tk.longitude,
                address_name = tk.addressName, is_deleted = tk.isDeleted,
                mod_time = toIso(tk.modTime) ?: EPOCH_ZERO, tag_ids = tagIds,
            )
        }

        val resp = try {
            syncApi.push(SyncPushBody(tasks = taskPush, tags = tagPush))
        } catch (e: Exception) {
            Log.e(TAG, "push failed; giữ dirty, thử lại lần sau", e)
            return
        }
        if (!resp.success) return

        // Dọn cờ local THEO status từng bản ghi server trả về — tránh mất dữ liệu nếu
        // 1 item lỗi (server vẫn trả HTTP 200). Item 'error'/không rõ → giữ dirty, retry.
        val taskStatus = resp.results?.tasks?.associate { it.id to it.status } ?: emptyMap()
        val tagStatus  = resp.results?.tags?.associate { it.id to it.status } ?: emptyMap()

        for (t in dirtyTags) {
            val s = tagStatus[t.id]
            if (t.isDeleted) { if (s != null && s in DELETE_OK) tagDao.hardDeleteById(t.id) }
            else { if (s != null && s in WRITE_OK) tagDao.markSynced(t.id) }
        }
        for (tk in dirtyTasks) {
            val s = taskStatus[tk.id]
            if (tk.isDeleted) { if (s != null && s in DELETE_OK) taskDao.hardDeleteById(tk.id) }
            else { if (s != null && s in WRITE_OK) taskDao.markSynced(tk.id) }
        }
    }

    // ── PULL ──────────────────────────────────────────────
    private suspend fun pull() {
        val since = prefs.getString(KEY_LAST_SYNC, EPOCH_ZERO) ?: EPOCH_ZERO
        val resp = try {
            syncApi.pull(since)
        } catch (e: Exception) {
            Log.e(TAG, "pull failed", e)
            return
        }
        if (!resp.success) return
        val changes = resp.changes ?: return

        // Tag trước (task gắn tag → tag phải có local trước khi tạo cross-ref).
        for (t in changes.tags) applyTag(t)
        for (tk in changes.tasks) applyTask(tk)

        resp.server_time?.let { prefs.edit().putString(KEY_LAST_SYNC, it).apply() }
    }

    private suspend fun applyTag(t: SyncTagPull) {
        val local = tagDao.getById(t.id)
        if (local?.isDirty == true) return                      // bảo vệ bản chưa push
        if (t.is_deleted) {
            if (local != null) tagDao.hardDeleteById(t.id)
            return
        }
        tagDao.upsert(
            LocalTag(
                id = t.id, tagName = t.name, colorHex = t.color_hex ?: "",
                modTime = fromIso(t.mod_time), isDeleted = false, isDirty = false,
            ),
        )
    }

    private suspend fun applyTask(tk: SyncTaskPull) {
        val local = taskDao.getTaskById(tk.id)
        if (local?.isDirty == true) return                      // bảo vệ bản chưa push
        if (tk.is_deleted) {
            if (local != null) taskDao.hardDeleteById(tk.id)    // CASCADE xoá cross-ref
            return
        }
        taskDao.upsertTask(
            LocalTask(
                id = tk.id, title = tk.title, description = tk.description ?: "",
                isCompleted = tk.is_completed, start = fromIso(tk.start_time),
                end = fromIso(tk.end_time), modTime = fromIso(tk.mod_time),
                priority = tk.priority ?: DEFAULT_PRIORITY,
                latitude = tk.latitude, longitude = tk.longitude,
                addressName = tk.address_name, isDeleted = false, isDirty = false,
            ),
        )
        // Dựng lại cross-ref local theo tập tag active server trả về.
        crossRefDao.deleteAllTagsByTaskId(tk.id)
        val now = System.currentTimeMillis()
        for (tag in tk.tags.orEmpty()) {
            // Chỉ gắn nếu tag tồn tại local (tránh vi phạm FK).
            if (tagDao.getById(tag.id) != null) {
                crossRefDao.upsert(LocalTaskTagCrossRef(taskId = tk.id, tagId = tag.id, modTime = now))
            }
        }
    }

    // ── ISO helpers (UTC) ─────────────────────────────────
    private fun toIso(epochMs: Long): String? =
        if (epochMs <= 0L) null else isoFmt().format(Date(epochMs))

    private fun fromIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(clean)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun isoFmt() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    companion object {
        private const val TAG = "SyncManager"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val EPOCH_ZERO = "1970-01-01T00:00:00Z"
        private const val DEFAULT_PRIORITY = 2
        // Status server cho phép dọn cờ local an toàn:
        private val WRITE_OK  = setOf("created", "updated", "stale")
        private val DELETE_OK = setOf("deleted", "skipped", "skipped_deleted")
    }
}
