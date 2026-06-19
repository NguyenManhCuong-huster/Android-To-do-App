package com.project.hustassistant.data.email.network

import com.project.hustassistant.data.attachment.network.NetworkAttachment
import com.project.hustassistant.data.attachment.toNetwork

/*
 * NetworkEmail.kt — domain trung gian + interface + impl.
 *
 * THAY ĐỔI 2025-05-02:
 *  - 1 row = 1 message.
 *
 * THAY ĐỔI 2026-05-23:
 *  - NetworkEmail kèm `attachments: List<NetworkAttachment>`. EmailRepository
 *    sẽ split ra và lưu vào attachment_dao trong cùng 1 transaction sync.
 */

data class NetworkEmail(
    val id: String,
    val accountId: String,
    val accountEmail: String?,
    val gmailMessageId: String?,
    val gmailThreadId: String?,
    val sender: String?,
    val recipient: String?,
    val subject: String?,
    val snippet: String?,
    val bodyText: String,
    val bodyHtml: String,
    val deepLinkIntent: String?,
    val receivedAt: String?,
    val isDeleted: Boolean,
    val isRead: Boolean = false,
    val attachments: List<NetworkAttachment> = emptyList(),
)

data class EmailPage(
    val emails: List<NetworkEmail>,
    val hasNext: Boolean,
    val total: Int,
    val page: Int,
)

data class NetworkThread(
    val threadId: String,
    val messages: List<NetworkEmail>,
)

interface EmailNetworkDataSource {
    suspend fun fetchEmails(page: Int = 1, limit: Int = 50, query: String? = null): EmailPage
    suspend fun fetchAllEmails(): List<NetworkEmail>
    suspend fun getEmail(id: String): NetworkEmail?
    suspend fun fetchThread(emailId: String): NetworkThread?
    suspend fun syncFromGmail(accountId: String? = null): EmailSyncSummary?
    suspend fun markAsRead(emailId: String)
}

class EmailRemoteDataSource(
    private val emailApi: EmailApi,
) : EmailNetworkDataSource {

    override suspend fun fetchEmails(page: Int, limit: Int, query: String?): EmailPage {
        val res = emailApi.list(page = page, limit = limit, q = query)
        return EmailPage(
            emails = res.data.map { it.toNetwork() },
            hasNext = res.meta.has_next,
            total = res.meta.total,
            page = res.meta.page,
        )
    }

    /**
     * Pull TẤT CẢ message + attachments từ /api/emails/all. Auto-paginate.
     */
    override suspend fun fetchAllEmails(): List<NetworkEmail> {
        val all = mutableListOf<NetworkEmail>()
        var page = 1
        while (true) {
            val res = emailApi.listAll(page = page, limit = 200)
            all += res.data.map { it.toNetwork() }
            if (!res.meta.has_next) break
            page++
            if (page > MAX_PAGES) break
        }
        return all
    }

    override suspend fun getEmail(id: String): NetworkEmail? =
        emailApi.getById(id).data?.toNetwork()

    override suspend fun fetchThread(emailId: String): NetworkThread? {
        val dto = emailApi.getThread(emailId).data ?: return null
        return NetworkThread(
            threadId = dto.thread_id,
            messages = dto.messages.map { it.toNetwork() },
        )
    }

    override suspend fun syncFromGmail(accountId: String?): EmailSyncSummary? =
        emailApi.sync(EmailSyncBody(account_id = accountId)).data

    override suspend fun markAsRead(emailId: String) {
        emailApi.markAsRead(emailId)
    }

    private fun EmailDto.toNetwork() = NetworkEmail(
        id = id,
        accountId = account_id,
        accountEmail = account_email,
        gmailMessageId = gmail_message_id,
        gmailThreadId = gmail_thread_id,
        sender = sender,
        recipient = recipient,
        subject = subject,
        snippet = snippet,
        bodyText = body_text.orEmpty(),
        bodyHtml = body_html.orEmpty(),
        deepLinkIntent = deep_link_intent,
        receivedAt = received_at,
        isDeleted = is_deleted ?: false,
        isRead = is_read ?: false,
        attachments = attachments?.toNetwork().orEmpty(),
    )

    companion object {
        private const val MAX_PAGES = 50
    }
}
