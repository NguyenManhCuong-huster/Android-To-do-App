package com.project3.todoapp.data

import com.project3.todoapp.data.local.LocalTask
import com.project3.todoapp.data.network.NetworkTask

fun Task.toLocal() = LocalTask(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime
)

fun LocalTask.toExternal() = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime
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
    modTime = modTime
)

fun NetworkTask.toExternal() = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end,
    modTime = modTime
)

@JvmName("networkListToExternal")
fun List<NetworkTask>.toExternal() = map(NetworkTask::toExternal)

@JvmName("taskListToExternal")
fun List<Task>.toNetwork() = map(Task::toNetwork)
