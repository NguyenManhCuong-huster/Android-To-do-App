package com.project3.todoapp.data.news

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.attachment.AttachmentRepository
import com.project3.todoapp.data.news.local.NewsDAO
import com.project3.todoapp.data.news.network.NetworkDataSource
import com.project3.todoapp.data.news.network.NetworkNews
import com.project3.todoapp.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DefaultNewsRepository — local-first, network khi có thể.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Sau khi refresh news từ server, sync attachments per news qua
 *    [AttachmentRepository.upsertForOwner]. Server đã embed attachments[]
 *    trong response /api/news → KHÔNG cần round-trip thêm.
 *  - 1 news fail attachment sync KHÔNG kill cả batch.
 */
class DefaultNewsRepository(
    private val networkDataSource: NetworkDataSource,
    private val localDataSource: NewsDAO,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
    private val attachmentRepository: AttachmentRepository,
) : NewsRepository {

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════

    override fun getNewsStream(kind: NewsKind?, tag: String?): Flow<List<News>> {
        tryRefresh()
        val source = when {
            kind != null && tag != null -> localDataSource.observeByKindAndTag(kind.name, tag)
            kind != null                -> localDataSource.observeByKind(kind.name)
            tag != null                 -> localDataSource.observeByTag(tag)
            else                        -> localDataSource.observeAll()
        }
        return source.map { list -> withContext(dispatcher) { list.toExternal() } }
    }

    override fun searchNews(query: String): Flow<List<News>> {
        tryRefresh()
        val q = "%${query.trim()}%"
        return localDataSource.search(q).map { list ->
            withContext(dispatcher) { list.toExternal() }
        }
    }

    override suspend fun getNews(id: String): News? = withContext(dispatcher) {
        localDataSource.getById(id)?.toExternal()
    }

    // ═════════════════════════════════════════════════════
    //  WRITE (chỉ reset local)
    // ═════════════════════════════════════════════════════

    override suspend fun deleteAllNews() {
        withContext(dispatcher) { localDataSource.deleteAll() }
    }

    // ═════════════════════════════════════════════════════
    //  SYNC
    // ═════════════════════════════════════════════════════

    private fun shouldSync(): Boolean =
        networkManager.isOnline() && authManager.isUserLoggedIn()

    private fun tryRefresh() {
        if (!shouldSync()) return
        scope.launch {
            try {
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Background refresh failed", e)
            }
        }
    }

    override suspend fun refresh() {
        if (!shouldSync()) return
        withContext(dispatcher) {
            val remote = networkDataSource.loadNews()
            if (remote.isEmpty()) {
                Log.w(TAG, "loadNews() returned empty; keep existing local cache")
                return@withContext
            }
            localDataSource.replaceAll(remote.toLocal())
            syncAttachments(remote)
            Log.d(TAG, "Refreshed ${remote.size} news from server")
        }
    }

    /** Replace attachments cho từng news. Resilient. */
    private suspend fun syncAttachments(remote: List<NetworkNews>) {
        for (n in remote) {
            try {
                attachmentRepository.upsertForOwner(
                    ownerType = "NEWS",
                    ownerId   = n.id,
                    remote    = n.attachments,
                )
            } catch (e: Exception) {
                Log.w(TAG, "syncAttachments fail for news ${n.id}: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "NewsRepo"
    }
}
