package com.project.hustassistant.data.task

import com.project.hustassistant.data.tag.toExternal
import com.project.hustassistant.data.task.local.LocalTask
import com.project.hustassistant.data.task.local.LocalTaskWithTags
import com.project.hustassistant.data.task.network.NetworkTask

/*
 * ModelMappingExt — chuyển đổi giữa 3 model:
 *
 *      LocalTask (Room) ◄──── Task (external) ────► NetworkTask (API)
 *
 * Task là model dùng trong app (UI, ViewModel). LocalTask và NetworkTask chỉ tồn tại
 * ở data layer. Mapping được tách thành extension function để repository và sync
 * code đọc dễ.
 *
 * Hai cờ chỉ có ở LocalTask:
 *   - isDirty:   task có thay đổi local chưa push lên server.
 *   - isDeleted: soft-delete cho UI, vẫn còn để sync push lệnh xoá.
 * Khi convert ngược về Task, hai cờ này bị bỏ (UI không quan tâm).
 */

// ─── Task ↔ LocalTask ──────────────────────────────────

/**
 * Tạo LocalTask từ Task (external).
 *
 * @param isDirty   true khi đây là thay đổi local chưa push (mặc định: true).
 * @param isDeleted true khi task này là soft-delete (mặc định: false).
 */
fun Task.toLocal(
    isDirty: Boolean = true,
    isDeleted: Boolean = false,
) = LocalTask(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime,
    priority = priority.value,
    latitude = latitude,
    longitude = longitude,
    addressName = addressName,
    isDeleted = isDeleted,
    isDirty = isDirty,
)

fun LocalTask.toExternal() = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime,
    priority = Priority.fromInt(priority),
    latitude = latitude,
    longitude = longitude,
    addressName = addressName,
)

@JvmName("localListToExternal")
fun List<LocalTask>.toExternal() = map(LocalTask::toExternal)

@JvmName("taskListToLocal")
fun List<Task>.toLocal() = map { it.toLocal() }

// ─── Task ↔ NetworkTask ────────────────────────────────

fun Task.toNetwork() = NetworkTask(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime,
    priority = priority.value,
    latitude = latitude,
    longitude = longitude,
    addressName = addressName,
    isDeleted = false,
)

fun NetworkTask.toExternal() = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime,
    priority = Priority.fromInt(priority),
    latitude = latitude,
    longitude = longitude,
    addressName = addressName,
)

@JvmName("networkListToExternal")
fun List<NetworkTask>.toExternal() = map(NetworkTask::toExternal)

@JvmName("taskListToNetwork")
fun List<Task>.toNetwork() = map(Task::toNetwork)

// ─── NetworkTask → LocalTask (dùng khi pull về từ server) ───

/**
 * Convert task từ server thẳng xuống LocalTask.
 * Mặc định isDirty=false vì đây là dữ liệu vừa pull từ server (đã sync).
 * isDeleted lấy từ tombstone server.
 */
fun NetworkTask.toLocal(isDirty: Boolean = false): LocalTask = LocalTask(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime,
    priority = priority,
    latitude = latitude,
    longitude = longitude,
    addressName = addressName,
    isDeleted = isDeleted,
    isDirty = isDirty,
)

// ─── LocalTaskWithTags (Room relation) → Task ──────────

fun LocalTaskWithTags.toExternal(): Task = Task(
    id = task.id,
    title = task.title,
    description = task.description,
    isCompleted = task.isCompleted,
    start = task.start,
    end = task.end,
    modTime = task.modTime,
    priority = Priority.fromInt(task.priority),
    latitude = task.latitude,
    longitude = task.longitude,
    addressName = task.addressName,
    tags = tags.toExternal(),
)

@JvmName("localTaskWithTagsListToExternal")
fun List<LocalTaskWithTags>.toExternal(): List<Task> = map { it.toExternal() }
