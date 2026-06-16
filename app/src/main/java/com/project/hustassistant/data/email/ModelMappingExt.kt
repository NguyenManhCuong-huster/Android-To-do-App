package com.project.hustassistant.data.email

import com.project.hustassistant.data.email.local.LocalEmail
import com.project.hustassistant.data.email.network.NetworkEmail
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * ModelMappingExt — Network ↔ Local ↔ Email (UI).
 *
 * THAY ĐỔI 2026-05-23:
 *  - LocalEmail.toThreadMessage() set localEmailId = LocalEmail.id (cần để
 *    ViewModel query attachments).
 */

private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun parseIsoToMillis(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
        isoFmt.parse(clean)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun millisToIso(ms: Long): String? {
    if (ms <= 0L) return null
    return isoFmt.format(java.util.Date(ms))
}

// ─── NetworkEmail → LocalEmail ──────────────────────────
fun NetworkEmail.toLocal(): LocalEmail = LocalEmail(
    id = id,
    accountId = accountId,
    accountEmail = accountEmail,
    gmailMessageId = gmailMessageId,
    gmailThreadId = gmailThreadId,
    sender = sender,
    recipient = recipient,
    subject = subject,
    snippet = snippet,
    bodyText = bodyText,
    bodyHtml = bodyHtml,
    deepLinkIntent = deepLinkIntent,
    receivedAt = parseIsoToMillis(receivedAt),
    modTime = System.currentTimeMillis(),
)

// ─── LocalEmail → Email (UI) ────────────────────────────
fun LocalEmail.toExternal(): Email = Email(
    id = id,
    accountId = accountId,
    accountEmail = accountEmail,
    gmailMessageId = gmailMessageId,
    gmailThreadId = gmailThreadId,
    sender = sender,
    subject = subject,
    snippet = snippet,
    deepLinkIntent = deepLinkIntent,
    receivedAt = millisToIso(receivedAt),
    modTime = modTime,
)

@JvmName("localEmailListToExternal")
fun List<LocalEmail>.toExternal(): List<Email> = map(LocalEmail::toExternal)

// ─── LocalEmail → ThreadMessage (UI cho thread reader) ──
fun LocalEmail.toThreadMessage() = ThreadMessage(
    localEmailId = id,                                    // ← MỚI
    gmailMessageId = gmailMessageId ?: id,
    sender = sender,
    recipient = recipient,
    subject = subject,
    bodyText = bodyText,
    bodyHtml = bodyHtml,
    snippet = snippet,
    receivedAt = millisToIso(receivedAt),
    deepLinkIntent = deepLinkIntent,
)
