package com.project.hustassistant.data.tag

import com.project.hustassistant.data.tag.local.LocalTag
import com.project.hustassistant.data.tag.network.NetworkTag

/*
 * ModelMappingExt — chuyển đổi giữa 3 model:
 *
 *      LocalTag (Room) ◄──── Tag (external) ────► NetworkTag (API)
 *
 * Tag là model dùng trong app (UI, ViewModel). LocalTag và NetworkTag chỉ tồn tại
 * ở data layer. Mapping được tách thành extension function để repository và sync
 * code đọc dễ.
 *
 * Lưu ý 2 cờ chỉ có ở LocalTag:
 *   - isDirty:   tag có thay đổi local chưa push lên server.
 *   - isDeleted: soft-delete cho UI, vẫn còn để sync push lệnh xoá.
 * Khi convert ngược về Tag, hai cờ này bị bỏ (UI không quan tâm).
 */

// ─── Tag ↔ LocalTag ────────────────────────────────────

/**
 * Tạo LocalTag từ Tag (external).
 *
 * @param isDirty   true khi đây là thay đổi local chưa push (mặc định: true).
 * @param isDeleted true khi tag này là soft-delete (mặc định: false).
 */
fun Tag.toLocal(
    isDirty: Boolean = true,
    isDeleted: Boolean = false,
): LocalTag = LocalTag(
    id = id,
    tagName = tagName,
    colorHex = colorHex,
    modTime = modTime,
    isDeleted = isDeleted,
    isDirty = isDirty,
)

fun LocalTag.toExternal(): Tag = Tag(
    id = id,
    tagName = tagName,
    colorHex = colorHex,
    modTime = modTime,
)

@JvmName("localListToExternal")
fun List<LocalTag>.toExternal(): List<Tag> = map(LocalTag::toExternal)

// ─── NetworkTag ↔ Tag ──────────────────────────────────

fun NetworkTag.toExternal(): Tag = Tag(
    id = id,
    tagName = name,
    colorHex = colorHex,
    modTime = modTime,
)

fun Tag.toNetwork(isDeleted: Boolean = false): NetworkTag = NetworkTag(
    id = id,
    name = tagName,
    colorHex = colorHex,
    isDeleted = isDeleted,
    modTime = modTime,
)

@JvmName("networkListToExternal")
fun List<NetworkTag>.toExternal(): List<Tag> = map(NetworkTag::toExternal)

// ─── NetworkTag → LocalTag (dùng khi pull về từ server) ───

/**
 * Convert tag từ server thẳng xuống LocalTag.
 * Mặc định isDirty=false vì đây là dữ liệu vừa pull từ server (đã sync).
 */
fun NetworkTag.toLocal(isDirty: Boolean = false): LocalTag = LocalTag(
    id = id,
    tagName = name,
    colorHex = colorHex,
    modTime = modTime,
    isDeleted = isDeleted,
    isDirty = isDirty,
)
