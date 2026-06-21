package com.project.hustassistant.data.task

import android.util.Log
import com.project.hustassistant.authentication.AuthManager
import com.project.hustassistant.data.task.local.LocalTask
import com.project.hustassistant.data.task.local.TaskDAO
import com.project.hustassistant.data.task.network.NetworkDataSource
import com.project.hustassistant.data.sync.SyncManager
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
    private val localDataSource: TaskDAO,
    private val syncManager: SyncManager,
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

    // Sync giờ do SyncManager điều phối tập trung (batch /api/sync, ordered tags→tasks,
    // LWW ở server, id ổn định nên KHÔNG còn reassignId). Repo chỉ kích hoạt.
    private fun trySync() {
        if (!shouldSync()) return
        scope.launch {
            try {
                syncManager.sync()
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed", e)
            }
        }
    }

    override suspend fun sync() = syncManager.sync()

    companion object {
        private const val TAG = "TaskRepo"
    }
}
