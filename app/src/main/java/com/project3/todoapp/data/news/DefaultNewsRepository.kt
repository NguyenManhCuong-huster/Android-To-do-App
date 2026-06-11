package com.project3.todoapp.data.news

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.attachment.AttachmentRepository
import com.project3.todoapp.data.news.local.NewsDAO
import com.project3.todoapp.data.news.local.NewsRecommendationDAO
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
 *  - Sau khi refresh news, sync attachments per news qua
 *    [AttachmentRepository.upsertForOwner]. Server đã embed attachments[]
 *    trong response /api/news → KHÔNG cần round-trip thêm.
 *  - 1 news fail attachment sync KHÔNG kill cả batch.
 *
 * THÊM MỚI: recommendations.
 *  - [getRecommendationsStream] observe DAO mới + trigger refresh ngầm.
 *  - [refreshRecommendations] pull /api/news/recommendations, UPSERT cả
 *    news (full data) lẫn news_recommendations.
 *  - [dismissRecommendation] update local trước (snappy), push server background.
 */
class DefaultNewsRepository(
    private val networkDataSource: NetworkDataSource,
    private val localDataSource: NewsDAO,
    private val recommendationDao: NewsRecommendationDAO,           // ← MỚI
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
        // 1) Local trước.
        localDataSource.getById(id)?.let { return@withContext it.toExternal() }

        // 2) MỚI 2026-06: cache miss -> fetch từ server (news do AI tra ra bằng
        //    search_news nhưng chưa nằm trong cache local). Best-effort.
        if (!shouldSync()) return@withContext null
        val remote = networkDataSource.loadNewsById(id) ?: return@withContext null
        runCatching {
            localDataSource.upsertAll(listOf(remote).toLocal())
            syncAttachments(listOf(remote))
        }
        remote.toExternal()
    }

    // ═════════════════════════════════════════════════════
    //  RECOMMENDATIONS
    // ═════════════════════════════════════════════════════

    override fun getRecommendationsStream(): Flow<List<News>> {
        tryRefreshRecommendations()
        return recommendationDao.observeRecommended().map { list ->
            withContext(dispatcher) { list.toExternal() }
        }
    }

    override suspend fun refreshRecommendations() {
        if (!shouldSync()) return
        withContext(dispatcher) {
            val remote = networkDataSource.loadRecommendations(limit = 50)
            // Nếu remote rỗng → có thể server lỗi HOẶC user chưa có profile.
            // Vẫn replace cache để clear UI (tránh hiển thị stale).
            if (remote.isEmpty()) {
                recommendationDao.deleteAll()
                Log.d(TAG, "loadRecommendations() returned empty")
                return@withContext
            }

            // 1. UPSERT news (vì có thể news đề xuất chưa nằm trong local cache).
            localDataSource.upsertAll(remote.toLocal())

            // 2. Replace recommendation cache (atomic).
            val recs = remote.mapNotNull { it.toLocalRecommendation() }
            recommendationDao.replaceAll(recs)

            // 3. Sync attachments giống refresh thường.
            syncAttachments(remote)

            Log.d(TAG, "Refreshed ${recs.size} recommendations")
        }
    }

    override suspend fun dismissRecommendation(newsId: String) {
        // Local trước (UI mất ngay, snappy)
        withContext(dispatcher) { recommendationDao.dismiss(newsId) }
        // Server sau (fire-and-forget, không block UI)
        scope.launch(dispatcher) {
            try {
                networkDataSource.dismissRecommendation(newsId)
            } catch (e: Exception) {
                Log.w(TAG, "dismiss server fail: ${e.message}")
            }
        }
    }

    private fun tryRefreshRecommendations() {
        if (!shouldSync()) return
        scope.launch {
            try {
                refreshRecommendations()
            } catch (e: Exception) {
                Log.e(TAG, "Background refresh recommendations failed", e)
            }
        }
    }

    // ═════════════════════════════════════════════════════
    //  WRITE (chỉ reset local)
    // ═════════════════════════════════════════════════════

    override suspend fun deleteAllNews() {
        withContext(dispatcher) {
            recommendationDao.deleteAll()       // ← MỚI: clear cùng news
            localDataSource.deleteAll()
        }
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
