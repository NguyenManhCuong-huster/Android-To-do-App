package com.project3.todoapp.data.tasktag

import com.project3.todoapp.data.tasktag.local.LocalTaskTagCrossRef


fun TaskTagCrossRef.toLocal(): LocalTaskTagCrossRef = LocalTaskTagCrossRef(
    taskId = taskId,
    tagId = tagId,
    modTime = modTime
)

fun LocalTaskTagCrossRef.toExternal(): TaskTagCrossRef = TaskTagCrossRef(
    taskId = taskId,
    tagId = tagId,
    modTime = modTime
)

fun List<TaskTagCrossRef>.toLocal(): List<LocalTaskTagCrossRef> = map { it.toLocal() }

fun List<LocalTaskTagCrossRef>.toExternal(): List<TaskTagCrossRef> = map { it.toExternal() }