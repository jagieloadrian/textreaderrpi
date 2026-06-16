package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthDetailResponse(
    val uptime: Long,
    val memoryUsed: Long,
    val memoryMax: Long,
    val displayStatus: String,
    val totalFailures: Long,
    val zoneErrors: Map<String, String?>,
)
