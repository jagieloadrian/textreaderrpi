package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthDetailResponse(
    val status: String,
    val uptime: Long,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
    val displayType: String,
    val isActive: Boolean,
    val lastError: String? = null,
)

