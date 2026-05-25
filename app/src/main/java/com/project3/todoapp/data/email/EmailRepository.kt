package com.project3.todoapp.data.email

import android.util.Log
import com.project3.todoapp.authentication.AuthManager
import com.project3.todoapp.data.attachment.AttachmentRepository
import com.project3.todoapp.data.email.local.EmailDAO
import com.project3.todoapp.data.email.network.EmailNetworkDataSource
import com.project3.todoapp.data.email.network.EmailSyncSummary
import com.project3.todoapp.data.email.network.NetworkEmail
import com.project3.todoapp.network.NetworkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * EmailRepository — local-first.
 *
 * THAY ĐỔI 2025-05-02:
 *  - Schema: 1 row = 1 message.
 *  - getThread(emailId): query Room theo gmailThreadId + receivedAt.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Sync metadata attachments cùng với email trong 1 lần fetchAllEmails().
 *    Sau khi upsert email, replace attachments của TỪNG email qua
 *    [AttachmentRepository.upsertForOwner].
 *  - Lý do dùng [AttachmentRepository] thay vì gọi DAO trực tiếp: giữ logic
 *    merge-preserve-cache ở 1 chỗ.
 */
class EmailRepository(
    private val emailDao: EmailDAO,
    private val networkDataSource: EmailNetworkDataSource,
    private val authManager: AuthManager,
    private val networkManager: NetworkManager,
    private val attachmentRepository: AttachmentRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private fun shouldSync(): Boolean =
        networkManager.isOnline() && authManager.isUserLoggedIn()

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════

    fun getEmailsStream(query: String? = null): Flow<List<Email>> {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }
        return emailDao.observeThreadHeads(q).map { it.toExternal() }
    }

    suspend fun getEmail(id: String): Email? = withContext(dispatcher) {
        emailDao.getById(id)?.toExternal()
    }

    suspend fun getThread(emailId: String): Result<List<ThreadMessage>> = runCatching {
        withContext(dispatcher) {
            val anchor = emailDao.getById(emailId)
                ?: error("Email không tồn tại trong local. Bấm Sync để tải về.")

            val threadId = anchor.gmailThreadId
            if (threadId.isNullOrBlank()) {
                listOf(anchor.toThreadMessage())
            } else {
                emailDao.getThreadUpTo(
                    threadId = threadId,
                    accountId = anchor.accountId,
                    untilEpoch = anchor.receivedAt,
                ).map { it.toThreadMessage() }
            }
        }
    }

    // ═════════════════════════════════════════════════════
    //  SYNC
    // ═════════════════════════════════════════════════════

    suspend fun sync(): Result<Int> = runCatching {
        if (!shouldSync()) {
            Log.d(TAG, "sync skipped: offline or not logged-in")
            return@runCatching emailDao.getAll().size
        }

        withContext(dispatcher) {
            val remoteEmails = networkDataSource.fetchAllEmails()

            if (remoteEmails.isEmpty()) {
                emailDao.deleteAll()
            } else {
                emailDao.upsertAll(remoteEmails.map { it.toLocal() })
                emailDao.deleteNotIn(remoteEmails.map { it.id })
                syncAttachments(remoteEmails)
            }

            emailDao.getAll().size
        }
    }

    /**
     * Với mỗi email, replace attachments tương ứng. Resilient — fail 1 email
     * không kill cả batch.
     *
     * Server đã trả `attachments` inline trong /api/emails/all → KHÔNG cần
     * round-trip thêm. Email không có file → attachments = emptyList →
     * upsertForOwner sẽ deleteForOwner (cleanup nếu trước đó có file mà giờ
     * không còn).
     */
    private suspend fun syncAttachments(emails: List<NetworkEmail>) {
        for (e in emails) {
            try {
                attachmentRepository.upsertForOwner(
                    ownerType = "EMAIL",
                    ownerId   = e.id,
                    remote    = e.attachments,
                )
            } catch (err: Exception) {
                Log.w(TAG, "syncAttachments fail for email ${e.id}: ${err.message}")
            }
        }
    }

    suspend fun syncFromGmail(accountId: String? = null): Result<EmailSyncSummary> = runCatching {
        withContext(dispatcher) {
            requireNotNull(networkDataSource.syncFromGmail(accountId)) { "Sync failed" }
        }
    }

    suspend fun deleteAllLocal() {
        withContext(dispatcher) { emailDao.deleteAll() }
    }

    companion object {
        private const val TAG = "EmailRepo"
    }
}
