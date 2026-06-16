package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastResult(
    val successful: List<String>,
    val failed: List<FailedZone>
)

@Serializable
data class FailedZone(
    val zoneId: String,
    val reason: String
)
