package com.project3.todoapp.data.attachment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.project3.todoapp.data.attachment.local.AttachmentDAO
import com.project3.todoapp.data.attachment.local.LocalAttachment
import com.project3.todoapp.data.attachment.network.AttachmentApi
import com.project3.todoapp.data.attachment.network.NetworkAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * AttachmentRepository — local-first cho metadata, lazy-download cho bytes.
 *
 * 3 trách nhiệm chính:
 *
 *  1) [observeForEmail] / [observeForNews]  — stream metadata từ Room cho UI.
 *
 *  2) [syncFromServer] / [upsertFromSync]   — ghi metadata từ server vào Room
 *     (gọi bởi EmailRepository và NewsRepository khi sync email/news).
 *
 *  3) [openAttachment]                       — flow user tap chip:
 *       a. Check local cache file tồn tại + valid (đúng size) → mở luôn.
 *       b. Nếu chưa cache → call API /download → ghi xuống cache dir → mở.
 *       c. Mở dùng FileProvider + Intent.ACTION_VIEW (system picker chọn app
 *          phù hợp với MIME type).
 *
 * Cache strategy:
 *  • Lưu trong `context.cacheDir/attachments/<attachmentId>/<fileName>`.
 *    Sub-dir theo id để 2 file cùng tên ở 2 email khác nhau không đụng.
 *  • Android tự dọn cacheDir khi máy hết space → KHÔNG đảm bảo persistent.
 *    Acceptable: lần sau user tap sẽ download lại.
 *  • Update Room: ghi absolute path → các tap sau mở instant.
 */
class AttachmentRepository(
    private val context: Context,
    private val attachmentDao: AttachmentDAO,
    private val attachmentApi: AttachmentApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ═════════════════════════════════════════════════════
    //  READ
    // ═════════════════════════════════════════════════════

    fun observeForEmail(emailId: String): Flow<List<Attachment>> =
        attachmentDao.observeForEmail(emailId).map { it.toExternal() }

    fun observeForNews(newsId: String): Flow<List<Attachment>> =
        attachmentDao.observeForNews(newsId).map { it.toExternal() }

    suspend fun getForEmail(emailId: String): List<Attachment> = withContext(dispatcher) {
        attachmentDao.getForOwner("EMAIL", emailId).toExternal()
    }

    suspend fun getForNews(newsId: String): List<Attachment> = withContext(dispatcher) {
        attachmentDao.getForOwner("NEWS", newsId).toExternal()
    }

    // ═════════════════════════════════════════════════════
    //  SYNC METADATA (gọi từ EmailRepo / NewsRepo)
    // ═════════════════════════════════════════════════════

    /**
     * Merge metadata mới từ server vào Room, GIỮ NGUYÊN localCachedPath nếu có.
     *
     * Pattern: với mỗi NetworkAttachment, lookup row cũ trong Room → nếu tồn tại
     * và đã có localCachedPath thì giữ. Sau đó replaceForOwner.
     */
    suspend fun upsertForOwner(
        ownerType: String,
        ownerId: String,
        remote: List<NetworkAttachment>,
    ) = withContext(dispatcher) {
        if (remote.isEmpty()) {
            attachmentDao.deleteForOwner(ownerType, ownerId)
            return@withContext
        }
        val existing = attachmentDao.getForOwner(ownerType, ownerId).associateBy { it.id }
        val merged = remote.map { net ->
            net.toLocal(existingLocalPath = existing[net.id]?.localCachedPath)
        }
        attachmentDao.replaceForOwner(ownerType, ownerId, merged)
    }

    /**
     * Lazy fetch metadata từ server (khi local rỗng). Best-effort.
     */
    suspend fun syncFromServer(ownerType: String, ownerId: String): Result<Int> = runCatching {
        withContext(dispatcher) {
            val res = attachmentApi.listByOwner(ownerType, ownerId)
            val list = res.data ?: emptyList()
            upsertForOwner(ownerType, ownerId, list.toNetwork())
            list.size
        }
    }

    // ═════════════════════════════════════════════════════
    //  DOWNLOAD + OPEN
    // ═════════════════════════════════════════════════════

    /**
     * Kết quả của [downloadIfNeeded] — caller decide làm gì tiếp.
     */
    sealed class DownloadResult {
        data class Ready(val file: File, val mimeType: String?) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
        object NotAvailable : DownloadResult()   // server chưa có file (is_downloaded=false)
    }

    /**
     * Đảm bảo có local file cho [attachmentId]. Nếu chưa → download.
     *
     * - Nếu Room có localCachedPath + file vật lý còn → return Ready.
     * - Nếu server.isDownloaded = false → NotAvailable (UI hiển thị "file too large /
     *   download fail on server").
     * - Else stream từ /api/attachments/:id/download → ghi cacheDir → update Room.
     */
    suspend fun downloadIfNeeded(attachmentId: String): DownloadResult = withContext(dispatcher) {
        val local = attachmentDao.getById(attachmentId)
            ?: return@withContext DownloadResult.Error("Attachment không tồn tại local. Hãy refresh.")

        // 1) Đã cache & file vẫn còn? → reuse
        if (!local.localCachedPath.isNullOrBlank()) {
            val cached = File(local.localCachedPath)
            if (cached.exists() && cached.length() > 0) {
                return@withContext DownloadResult.Ready(cached, local.mimeType)
            }
        }

        // 2) Server-side ready?
        if (!local.isDownloaded) {
            return@withContext DownloadResult.NotAvailable
        }

        // 3) Stream từ server
        val response = try {
            attachmentApi.download(attachmentId)
        } catch (e: Exception) {
            Log.e(TAG, "download network error", e)
            return@withContext DownloadResult.Error("Lỗi mạng khi tải file: ${e.message}")
        }
        if (!response.isSuccessful) {
            val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
            Log.w(TAG, "download HTTP ${response.code()}: $errBody")
            return@withContext DownloadResult.Error("Server trả lỗi ${response.code()}.")
        }
        val body = response.body() ?: return@withContext DownloadResult.Error("Response rỗng.")

        // Ghi xuống cacheDir/attachments/<id>/<filename>
        val targetDir = File(context.cacheDir, "attachments/$attachmentId")
        if (!targetDir.exists()) targetDir.mkdirs()
        val safeName = local.fileName.replace(Regex("[^\\p{L}\\p{N}._\\-()\\s]"), "_")
        val outFile = File(targetDir, safeName)

        try {
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            }
        } catch (e: Exception) {
            outFile.delete()
            Log.e(TAG, "write file failed", e)
            return@withContext DownloadResult.Error("Lỗi ghi file: ${e.message}")
        }

        // Update Room với cached path
        attachmentDao.updateLocalPath(attachmentId, outFile.absolutePath)

        // MIME type ưu tiên server, fallback đoán từ extension
        val mime = local.mimeType ?: guessMimeFromName(local.fileName)
        DownloadResult.Ready(outFile, mime)
    }

    // ═════════════════════════════════════════════════════
    //  HELPERS (intent / mime)
    // ═════════════════════════════════════════════════════

    /**
     * Build Intent.ACTION_VIEW để mở file. Dùng FileProvider (an toàn cho
     * Android N+: app khác KHÔNG được phép truy cập file:// URI trực tiếp).
     *
     * Caller (Activity) gọi startActivity(intent) trong try/catch để fallback
     * khi không có app nào handle MIME này (vd file lạ → user thấy "no app").
     */
    fun buildOpenIntent(file: File, mimeType: String?): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val safeMime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, safeMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Build Intent.ACTION_SEND để share file (chia sẻ qua app khác).
     */
    fun buildShareIntent(file: File, mimeType: String?): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun guessMimeFromName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    companion object {
        private const val TAG = "AttachmentRepo"
    }
}
