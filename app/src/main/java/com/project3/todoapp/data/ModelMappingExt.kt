package com.project3.todoapp.data

import com.project3.todoapp.data.local.LocalTask

fun Task.toLocal() = LocalTask(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end
)

fun LocalTask.toExternal() = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    start = start,
    end = end
)

fun List<LocalTask>.toExternal() = map(LocalTask::toExternal)

