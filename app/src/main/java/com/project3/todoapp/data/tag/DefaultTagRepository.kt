package com.project3.todoapp.data.tag

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.tag.local.LocalTag
import com.project3.todoapp.data.tag.local.TagDAO
import com.project3.todoapp.data.tag.network.NetworkDataSource
import com.project3.todoapp.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * DefaultTagRepository — local-first, network khi có thể.
 *
 * ════════════════════════════════════════════════════════════════
 *  CHIẾN LƯỢC OFFLINE-FIRST
 * ════════════════════════════════════════════════════════════════
 * Mọi WRITE:
 *   1) Cập nhật Room NGAY (UI thấy thay đổi tức thì, app chạy được khi offline).
 *   2) Đặt isDirty=true trên LocalTag.
 *   3) Fire-and-forget gọi [trySync] — nếu online + đã login thì push/pull.
 *
 * Mọi READ:
 *   - Lấy thẳng từ Room → app hoạt động bình thường khi không có mạng.
 *
 * SYNC ([sync]):
 *   - PUSH: với mỗi LocalTag dirty:
 *       • isDeleted=true        → DELETE /api/tags/{id} → hard-delete local
 *       • id chưa có ở server   → POST   /api/tags     → đổi id local theo server cấp
 *       • id đã có ở server     → PUT    /api/tags/{id} → mark synced
 *   - PULL: GET /api/tags?include_deleted=true → upsert local (Last-Write-Wins theo modTime).
 *
 *  ⚠️  NOTE về ID:
 *  Server hiện không nhận client-supplied id ở POST /api/tags. Khi tạo offline, ta
 *  dùng UUID v4 client. Lúc push, server sẽ cấp id khác → ta đổi id local qua
 *  [TagDAO.reassignId]. Việc này yêu cầu task_tag_cross_ref khai báo
 *  `onUpdate = ForeignKey.CASCADE` để cross-ref tự đổi theo (đã làm trong
 *  LocalTaskTagCrossRef). Nếu sau này server hỗ trợ idempotency-key/POST nhận id,
 *  có thể bỏ bước reassignId.
 */
class DefaultTagRepository(
    private val tagDao: TagDAO,
    private val networkDataSource: NetworkDataSource,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TagRepository {

    // ═════════════════════════════════════════════════════
    //  WRITE
    // ═════════════════════════════════════════════════════

    override suspend fun createTag(name: String, colorHex: String): String =
        withContext(dispatcher) {
            val tagId = UUID.randomUUID().toString()
            val tag = Tag(
                id = tagId,
                tagName = name,
                colorHex = colorHex,
                modTime = System.currentTimeMillis(),
            )
            // 1) LOCAL TRƯỚC — UI cập nhật ngay, app dùng được offline
            tagDao.upsert(tag.toLocal(isDirty = true, isDeleted = false))
            // 2) Best-effort push lên server (background)
            trySync()
            tagId
        }

    override suspend fun updateTag(tagId: String, name: String, colorHex: String) {
        withContext(dispatcher) {
            val tag = Tag(
                id = tagId,
                tagName = name,
                colorHex = colorHex,
                modTime = System.currentTimeMillis(),
            )
            tagDao.upsert(tag.toLocal(isDirty = true, isDeleted = false))
            trySync()
        }
    }

    override suspend fun deleteTag(tagId: String) {
        withContext(dispatcher) {
            // SOFT-delete: ẩn khỏi UI, vẫn giữ record để push lệnh DELETE lên server.
            // Khi sync push thành công, sẽ hard-delete local.
            tagDao.softDelete(tagId, System.currentTimeMillis())
            trySync()
        }
    }

    override suspend fun deleteAllTags() {
        // Reset local — KHÔNG đụng vào server (dùng cho đăng xuất, đổi user).
        withContext(dispatcher) { tagDao.deleteAll() }
    }

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════

    override suspend fun getTag(tagId: String): Tag? = withContext(dispatcher) {
        tagDao.getById(tagId)
            ?.takeIf { !it.isDeleted }
            ?.toExternal()
    }

    override fun getTagsStream(): Flow<List<Tag>> {
        // Trigger sync ngầm khi UI bắt đầu observe (refresh data từ server)
        trySync()
        return tagDao.observeAll().map { it.toExternal() }
    }

    override suspend fun getTags(): List<Tag> = withContext(dispatcher) {
        tagDao.getAll().toExternal()
    }

    override suspend fun getTagsByTask(taskId: String): List<Tag> = withContext(dispatcher) {
        tagDao.getTagsForTask(taskId).toExternal()
    }

    // ═════════════════════════════════════════════════════
    //  SYNC
    // ═════════════════════════════════════════════════════

    /** Có nên gọi server không? Cần online + đã login (có JWT). */
    private fun shouldSync(): Boolean =
        networkManager.isOnline() && authManager.isUserLoggedIn()

    /** Background sync — không block caller, swallow lỗi. */
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
            // 1) PUSH local → server (đẩy mọi dirty trước để không bị PULL ghi đè)
            pushLocalChanges()
            // 2) PULL server → local (LWW)
            pullRemoteChanges()
        }
    }

    /**
     * PUSH: đẩy mọi LocalTag dirty lên server.
     *
     * Phân biệt POST/PUT/DELETE bằng cách so id local với danh sách id server hiện có:
     *  - id ∉ server → POST (tag tạo offline)
     *  - id ∈ server + isDeleted → DELETE
     *  - id ∈ server + !isDeleted → PUT
     */
    private suspend fun pushLocalChanges() {
        val dirty = tagDao.getDirtyTags()
        if (dirty.isEmpty()) return

        // Lấy snapshot id server (1 round-trip duy nhất cho cả batch push)
        val remoteIds: Set<String> = networkDataSource.loadTags()
            .filter { !it.isDeleted }
            .map { it.id }
            .toSet()

        for (local in dirty) {
            try {
                pushOne(local, remoteIds)
            } catch (e: Exception) {
                Log.e(TAG, "Push tag ${local.id} failed; will retry next sync", e)
                // Để dirty=true → lần sync sau sẽ thử lại
            }
        }
    }

    private suspend fun pushOne(local: LocalTag, remoteIds: Set<String>) {
        when {
            // (1) Đã xoá local
            local.isDeleted -> {
                if (local.id in remoteIds) {
                    val ok = networkDataSource.deleteTag(local.id)
                    if (ok) tagDao.hardDeleteById(local.id)
                } else {
                    // Tag tạo offline rồi xoá luôn → server không biết, xoá thẳng local
                    tagDao.hardDeleteById(local.id)
                }
            }
            // (2) Tag tạo offline → POST, đổi id theo server cấp
            local.id !in remoteIds -> {
                val remote = networkDataSource.createTag(local.tagName, local.colorHex)
                when {
                    remote == null -> Unit // lỗi → giữ dirty=true, retry sau
                    remote.id != local.id -> tagDao.reassignId(local.id, remote.id)
                    else -> tagDao.markSynced(local.id)
                }
            }
            // (3) Tag đã có trên server → PUT
            else -> {
                val remote = networkDataSource.updateTag(
                    id = local.id,
                    name = local.tagName,
                    colorHex = local.colorHex,
                )
                if (remote != null) tagDao.markSynced(local.id)
            }
        }
    }

    /**
     * PULL: kéo tag từ server về local theo Last-Write-Wins.
     *
     * Quy tắc:
     *  - remote.isDeleted=true       → hard-delete local nếu có
     *  - local null                  → upsert remote
     *  - remote.modTime > local.modTime → upsert remote (server mới hơn)
     *  - ngược lại                   → giữ local (đang dirty hoặc local mới hơn)
     */
    private suspend fun pullRemoteChanges() {
        val remoteTags = networkDataSource.loadTags()
        if (remoteTags.isEmpty()) return

        for (remote in remoteTags) {
            val local = tagDao.getById(remote.id)
            when {
                remote.isDeleted -> {
                    if (local != null) tagDao.hardDeleteById(remote.id)
                }

                local == null -> {
                    tagDao.upsert(remote.toLocal(isDirty = false))
                }

                remote.modTime > local.modTime -> {
                    tagDao.upsert(remote.toLocal(isDirty = false))
                }
                // else: giữ local (local mới hơn hoặc đang dirty chờ push lại)
            }
        }
    }

    companion object {
        private const val TAG = "TagRepo"
    }
}
