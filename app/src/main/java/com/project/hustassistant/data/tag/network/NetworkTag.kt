package com.project.hustassistant.data.tag.network

/**
 * NetworkTag — DTO trung gian giữa repository và Retrofit layer.
 *
 * Tách khỏi [com.project.hustassistant.network.TagDto] (raw từ JSON server) để repository
 * không phụ thuộc vào tên field của API, dễ test bằng fake data source.
 */
data class NetworkTag(
    val id: String,
    val name: String,
    val colorHex: String,
    val isDeleted: Boolean,
    val modTime: Long, // epoch millis
)
