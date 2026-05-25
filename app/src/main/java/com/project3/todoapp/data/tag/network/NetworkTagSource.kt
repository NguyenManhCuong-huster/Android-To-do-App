package com.project3.todoapp.data.tag.network

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * NetworkTagSource — implementation thật của [NetworkDataSource], gọi REST qua [TagApi].
 *
 * TagApi và TagDto / CreateTagBody / UpdateTagBody nằm CÙNG package này (data.tag.network)
 * → không cần import. Trước đây chúng ở `com.project3.todoapp.network` (chung với
 * ApiClient/AuthApi) → đã tách ra theo feature.
 *
 * Mọi method bắt exception và trả giá trị "trống" (null/false/emptyList) để
 * caller (repository) không phải xử lý try/catch — phù hợp chiến lược local-first
 * best-effort sync.
 */
class NetworkTagSource(
    private val tagApi: TagApi,
) : NetworkDataSource {

    override suspend fun loadTags(): List<NetworkTag> = try {
        tagApi.list(includeDeleted = true).data
            ?.map { it.toNetworkTag() }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun createTag(name: String, colorHex: String): NetworkTag? = try {
        tagApi.create(CreateTagBody(name = name, color_hex = colorHex))
            .data?.toNetworkTag()
    } catch (_: Exception) {
        null
    }

    override suspend fun updateTag(
        id: String,
        name: String,
        colorHex: String,
    ): NetworkTag? = try {
        tagApi.update(id, UpdateTagBody(name = name, color_hex = colorHex))
            .data?.toNetworkTag()
    } catch (_: Exception) {
        null
    }

    override suspend fun deleteTag(id: String): Boolean = try {
        tagApi.delete(id).success
    } catch (_: Exception) {
        false
    }

    // ─── Helpers ─────────────────────────────────────────
    private fun TagDto.toNetworkTag() = NetworkTag(
        id = id,
        name = name,
        colorHex = color_hex ?: DEFAULT_COLOR,
        isDeleted = is_deleted ?: false,
        modTime = parseIso(mod_time),
    )

    private fun parseIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = iso.substringBefore('.').substringBefore('Z').substringBefore('+')
            fmt.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private const val DEFAULT_COLOR = "#9E9E9E"
    }
}
