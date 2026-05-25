package com.project3.todoapp.data.task

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.task.local.LocalTask
import com.project3.todoapp.data.task.local.TaskDAO
import com.project3.todoapp.data.task.network.NetworkDataSource
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
 * DefaultTaskRepository — local-first, network khi có thể.
 *
 * ════════════════════════════════════════════════════════════════
 *  CHIẾN LƯỢC OFFLINE-FIRST (giống DefaultTagRepository)
 * ════════════════════════════════════════════════════════════════
 * Mọi WRITE:
 *   1) Cập nhật Room NGAY với isDirty=true (UI thấy thay đổi tức thì, app chạy được offline).
 *   2) Fire-and-forget gọi [trySync] — nếu online + đã login thì push/pull.
 *
 * Mọi READ:
 *   - Lấy thẳng từ Room (đã lọc isDeleted=0 ở DAO) → app hoạt động bình thường khi offline.
 *
 * SYNC ([sync]):
 *   - PUSH: với mỗi LocalTask dirty:
 *       • isDeleted=true       → DELETE /api/tasks/{id} → hard-delete local
 *       • id chưa có ở server  → POST   /api/tasks    → đổi id local theo server cấp
 *       • id đã có ở server    → PUT    /api/tasks/{id} → markSynced
 *   - PULL: GET /api/tasks?include_deleted=true → upsert local (Last-Write-Wins theo modTime).
 *
 * ════════════════════════════════════════════════════════════════
 *  NHỮNG THAY ĐỔI SO VỚI BẢN CŨ (fix bug "không xóa được" + "nhân bản")
 * ════════════════════════════════════════════════════════════════
 *  1) [deleteTask] giờ là SOFT-DELETE (TaskDAO.softDelete) thay vì hard-delete.
 *     Bản cũ hard-delete local rồi gọi saveTasksToNetwork, nhưng saveTasksToNetwork
 *     chỉ POST task mới — không gọi DELETE → server không biết, lần sync sau pull về
 *     lại task đã xoá → "task hồi sinh".
 *
 *  2) PUSH gọi đúng endpoint POST/PUT/DELETE với id, không còn "POST tất cả task chưa
 *     có ở server" + "bỏ qua task đã có id ở server".
 *     Bản cũ POST không truyền id → server tự sinh id mới, mỗi lần sync nhân bản thêm.
 *     Update không bao giờ được push vì task đã có id ở server thì bị `continue`.
 *
 *  3) Bỏ method [TaskDAO.syncTasks] (deleteAll + upsertAll). Pattern đó wipe luôn
 *     isDirty của task mới tạo offline → các task đó bị mất sau lần sync đầu.
 *
 *  4) Server LWW (middleware lwwCheck) được kích hoạt qua header `x-client-mod-time`.
 *
 *  5) Sau migration 003_add_task_priority_location.sql, server hỗ trợ đầy đủ
 *     priority + latitude + longitude + address_name → bỏ workaround "bảo tồn giá trị
 *     local khi pull về" trong [pullRemoteChanges].
 *
 *  ⚠️  NOTE về ID:
 *  Server không nhận client-supplied id ở POST /api/tasks. Khi tạo offline ta dùng
 *  UUID v4 client. Lúc push, server cấp id khác → ta đổi id local qua
 *  [TaskDAO.reassignId]. Việc này yêu cầu task_tag_cross_ref khai báo
 *  `onUpdate = ForeignKey.CASCADE` để cross-ref tự đổi theo (đã có trong
 *  LocalTaskTagCrossRef).
 */
class DefaultTaskRepository(
    private val networkDataSource: NetworkDataSource,
    private val localDataSource: TaskDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
) : TaskRepository {

    // ═════════════════════════════════════════════════════
    //  WRITE
    // ═════════════════════════════════════════════════════

    override suspend fun createTask(
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority,
        addressName: String?,
    ): String = withContext(dispatcher) {
        val taskId = UUID.randomUUID().toString()
        val task = Task(
            id = taskId,
            title = title,
            description = description,
            start = start,
            end = end,
            modTime = System.currentTimeMillis(),
            priority = priority,
            addressName = addressName,
            isCompleted = false,
            latitude = null,
            longitude = null,
        )
        // 1) LOCAL TRƯỚC — UI cập nhật ngay, app dùng được offline
        localDataSource.upsertTask(task.toLocal(isDirty = true, isDeleted = false))
        // 2) Best-effort push lên server (background)
        trySync()
        taskId
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        start: Long,
        end: Long,
        priority: Priority,
        addressName: String?,
    ) {
        withContext(dispatcher) {
            val old = localDataSource.getTaskById(taskId)
                ?: throw IllegalStateException("Task ($taskId) not found")
            val updated = old.copy(
                title = title,
                description = description,
                start = start,
                end = end,
                priority = priority.value,
                addressName = addressName,
                modTime = System.currentTimeMillis(),
                isDirty = true,
                isDeleted = false,
            )
            localDataSource.upsertTask(updated)
            trySync()
        }
    }

    override suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean) {
        withContext(dispatcher) {
            localDataSource.updateTaskCompletion(
                taskId = taskId,
                completed = isCompleted,
                modTime = System.currentTimeMillis(),
            )
            trySync()
        }
    }

    override suspend fun deleteTask(taskId: String) {
        withContext(dispatcher) {
            // SOFT-delete: ẩn khỏi UI, giữ record để push DELETE lên server.
            // Khi sync push thành công, sẽ hard-delete local trong pushOne().
            localDataSource.softDelete(taskId, System.currentTimeMillis())
            trySync()
        }
    }

    override suspend fun deleteAllTasks() {
        // Reset toàn bộ cache local — KHÔNG đụng vào server (dùng cho đăng xuất / đổi user).
        withContext(dispatcher) { localDataSource.deleteAll() }
    }

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════

    override suspend fun getTask(taskId: String): Task? = withContext(dispatcher) {
        localDataSource.getTaskWithTagsById(taskId)?.toExternal()
    }

    override suspend fun getTasks(): List<Task> = withContext(dispatcher) {
        trySync()
        localDataSource.getAll().toExternal()
    }

    override fun getTasksStream(): Flow<List<Task>> {
        // Trigger sync ngầm khi UI bắt đầu observe (refresh data từ server)
        trySync()
        return localDataSource.observeTasksWithTags().map { list ->
            withContext(dispatcher) { list.toExternal() }
        }
    }

    override fun getTasksByTagStream(tagId: String): Flow<List<Task>> =
        localDataSource.getTasksForTag(tagId).map { it.toExternal() }

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
            // 2) PULL server → local (LWW theo modTime)
            pullRemoteChanges()
        }
    }

    /**
     * PUSH: đẩy mọi LocalTask dirty lên server.
     *
     * Phân biệt POST/PUT/DELETE bằng cách so id local với danh sách id server hiện có:
     *  - id ∉ server → POST (task tạo offline)
     *  - id ∈ server + isDeleted → DELETE
     *  - id ∈ server + !isDeleted → PUT
     */
    private suspend fun pushLocalChanges() {
        val dirty = localDataSource.getDirtyTasks()
        if (dirty.isEmpty()) return

        // Snapshot id server (1 round-trip duy nhất cho cả batch push)
        val remoteIds: Set<String> = networkDataSource.loadTasks()
            .filter { !it.isDeleted }
            .map { it.id }
            .toSet()

        for (local in dirty) {
            try {
                pushOne(local, remoteIds)
            } catch (e: Exception) {
                Log.e(TAG, "Push task ${local.id} failed; will retry next sync", e)
                // Để dirty=true → lần sync sau sẽ thử lại
            }
        }
    }

    private suspend fun pushOne(local: LocalTask, remoteIds: Set<String>) {
        when {
            // (1) Đã xoá local
            local.isDeleted -> {
                if (local.id in remoteIds) {
                    val ok = networkDataSource.deleteTask(local.id, local.modTime)
                    if (ok) localDataSource.hardDeleteById(local.id)
                } else {
                    // Task tạo offline rồi xoá luôn → server không biết, xoá thẳng local
                    localDataSource.hardDeleteById(local.id)
                }
            }

            // (2) Task tạo offline → POST, đổi id theo server cấp
            local.id !in remoteIds -> {
                val external = local.toExternal()
                val remote = networkDataSource.createTask(external)
                when {
                    remote == null -> Unit // lỗi → giữ dirty=true, retry sau
                    remote.id != local.id -> localDataSource.reassignId(local.id, remote.id)
                    else -> localDataSource.markSynced(local.id)
                }
            }

            // (3) Task đã có trên server → PUT
            else -> {
                val external = local.toExternal()
                val remote = networkDataSource.updateTask(external)
                if (remote != null) {
                    localDataSource.markSynced(local.id)
                }
                // Nếu null (vd 409 LWW conflict) → giữ dirty.
                // pullRemoteChanges sẽ xử lý ở vòng kế tiếp:
                //   server.modTime > local.modTime + !isDirty → ghi đè bằng server,
                //   nhưng vì còn isDirty=true ở đây nên local không bị ghi đè ngay.
                //   Lần sync sau push lại với modTime đã cập nhật.
            }
        }
    }

    /**
     * PULL: kéo task từ server về local theo Last-Write-Wins.
     *
     * Quy tắc:
     *  - remote.isDeleted=true                         → hard-delete local nếu có
     *  - local null                                    → upsert remote
     *  - remote.modTime > local.modTime && !isDirty    → upsert remote (server mới hơn)
     *  - ngược lại                                     → giữ local (đang dirty hoặc local mới hơn)
     */
    private suspend fun pullRemoteChanges() {
        val remoteTasks = networkDataSource.loadTasks()
        if (remoteTasks.isEmpty()) return

        for (remote in remoteTasks) {
            val local = localDataSource.getTaskById(remote.id)
            when {
                remote.isDeleted -> {
                    if (local != null) localDataSource.hardDeleteById(remote.id)
                }

                local == null -> {
                    localDataSource.upsertTask(remote.toLocal(isDirty = false))
                }

                remote.modTime > local.modTime && !local.isDirty -> {
                    // Server mới hơn và local KHÔNG còn dirty → ghi đè bằng giá trị server.
                    // Sau migration 003, server đã sync đủ priority/location nên không cần
                    // bảo tồn các field đó từ local nữa.
                    localDataSource.upsertTask(remote.toLocal(isDirty = false))
                }
                // else: giữ local (local mới hơn hoặc đang dirty chờ push lại)
            }
        }
    }

    companion object {
        private const val TAG = "TaskRepo"
    }
}
