package com.project3.todoapp.data.tag

/**
 * Tag — model dùng trong toàn bộ app (UI / ViewModel / domain layer).
 *
 * Đây là "external model" — không biết gì về Room hay Retrofit.
 * Dùng [ModelMappingExt.kt] để convert sang [LocalTag] (Room) hoặc [NetworkTag] (API).
 */
data class Tag(
    val id: String,
    val tagName: String,
    val colorHex: String,
    val modTime: Long,
)
