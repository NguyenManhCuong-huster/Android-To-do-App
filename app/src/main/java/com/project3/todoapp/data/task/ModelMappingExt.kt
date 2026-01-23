package com.project3.todoapp.data.task

import com.project3.todoapp.data.tag.toExternal
import com.project3.todoapp.data.task.local.LocalTask
import com.project3.todoapp.data.task.local.LocalTaskWithTags
import com.project3.todoapp.data.task.network.NetworkTask

fun Task.toLocal() = LocalTask(
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
    addressName = addressName
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
    addressName = addressName
)

@JvmName("localListToExternal")
fun List<LocalTask>.toExternal() = map(LocalTask::toExternal)

@JvmName("taskListToLocal")
fun List<Task>.toLocal() = map(Task::toLocal)

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
    addressName = addressName
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
    addressName = addressName
)

@JvmName("networkListToExternal")
fun List<NetworkTask>.toExternal() = map(NetworkTask::toExternal)

@JvmName("taskListToExternal")
fun List<Task>.toNetwork() = map(Task::toNetwork)

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
    tags = tags.toExternal() // Map list LocalTag sang Tag
)

@JvmName("localTaskWithTagsListToExternal")
fun List<LocalTaskWithTags>.toExternal(): List<Task> = map { it.toExternal() }
