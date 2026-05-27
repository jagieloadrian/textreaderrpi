package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    val uptime: Long,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
)

@Serializable
data class ReadinessStatus(
    val status: String,
    val displayType: String?,
    val isActive: Boolean,
    val lastError: String?,
)

