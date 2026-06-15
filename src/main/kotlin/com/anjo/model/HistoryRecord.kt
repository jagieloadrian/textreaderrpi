package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryRecord(
    val id: String = "",
    val text: String,
    val effect: String,
    val source: String,
    val scheduleId: String? = null,
    val zoneId: String? = null,
    val displayedAt: String = ""
)

@Serializable
data class HistoryPageResponse(
    val items: List<HistoryRecord>,
    val page: Int,
    val size: Int,
    val total: Long
)
