package com.project3.todoapp.data.tag

import com.project3.todoapp.data.tag.local.LocalTag


fun Tag.toLocal(): LocalTag = LocalTag(
    id = id,
    tagName = tagName,
    colorHex = colorHex,
    modTime = modTime
)

fun LocalTag.toExternal(): Tag = Tag(
    id = id,
    tagName = tagName,
    colorHex = colorHex,
    modTime = modTime
)

fun List<LocalTag>.toExternal(): List<Tag> = map { it.toExternal() }

fun List<Tag>.toLocal(): List<LocalTag> = map { it.toLocal() }