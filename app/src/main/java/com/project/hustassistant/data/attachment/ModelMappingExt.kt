package com.project.hustassistant.data.attachment

import com.project.hustassistant.data.attachment.local.LocalAttachment
import com.project.hustassistant.data.attachment.network.AttachmentDto
import com.project.hustassistant.data.attachment.network.NetworkAttachment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * ModelMappingExt — chuyển đổi giữa 3 model của Attachment:
 *
 *      LocalAttachment (Room) ──► Attachment (UI) ◄── NetworkAttachment (API)
 *
 * Quy ước giống Email/News: server snake_case, client camelCase, Room camelCase.
 */

private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseIso(iso: String?): Long {
    if (iso.isNullOrBlank()) return System.currentTimeMillis()
    return try {
        val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
        isoFmt.parse(clean)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

// ─── DTO → NetworkAttachment ───────────────────────────
fun AttachmentDto.toNetwork(): NetworkAttachment = NetworkAttachment(
    id           = id,
    ownerType    = owner_type,
    ownerId      = owner_id,
    fileName     = file_name,
    mimeType     = mime_type,
    sizeBytes    = size_bytes,
    isDownloaded = is_downloaded,
    isInline     = is_inline ?: false,
    sourceUrl    = source_url,
    modTime      = parseIso(mod_time ?: created_at),
)

@JvmName("dtoListToNetwork")
fun List<AttachmentDto>.toNetwork(): List<NetworkAttachment> = map(AttachmentDto::toNetwork)

// ─── NetworkAttachment → LocalAttachment ───────────────
/**
 * @param existingLocalPath Path cache đã có từ trước (nếu user đã tải về máy).
 *                          Pass null khi mới sync; pass previousLocal.localCachedPath
 *                          khi merge để không mất cache.
 */
fun NetworkAttachment.toLocal(existingLocalPath: String? = null): LocalAttachment = LocalAttachment(
    id              = id,
    ownerType       = ownerType,
    ownerId         = ownerId,
    fileName        = fileName,
    mimeType        = mimeType,
    sizeBytes       = sizeBytes,
    isDownloaded    = isDownloaded,
    isInline        = isInline,
    sourceUrl       = sourceUrl,
    modTime         = modTime,
    localCachedPath = existingLocalPath,
)

// ─── LocalAttachment → Attachment (UI) ─────────────────
fun LocalAttachment.toExternal(): Attachment = Attachment(
    id              = id,
    ownerType       = AttachmentOwner.fromServer(ownerType),
    ownerId         = ownerId,
    fileName        = fileName,
    mimeType        = mimeType,
    sizeBytes       = sizeBytes,
    isDownloaded    = isDownloaded,
    isInline        = isInline,
    sourceUrl       = sourceUrl,
    modTime         = modTime,
    localCachedPath = localCachedPath,
)

@JvmName("localListToExternal")
fun List<LocalAttachment>.toExternal(): List<Attachment> = map(LocalAttachment::toExternal)
