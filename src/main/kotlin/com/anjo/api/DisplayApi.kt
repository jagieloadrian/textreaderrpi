package com.anjo.api

import kotlinx.serialization.Serializable

@Serializable
data class DisplayStatusResponse(
    val displayType: String,
    val isActive: Boolean,
    val hardwareAvailable: Boolean,
    val currentMessage: String? = null,
    val error: String? = null,
)

@Serializable
data class DisplaySelectRequest(
    val type: String,
)

@Serializable
data class DisplaySelectResponse(
    val accepted: Boolean,
    val message: String,
)

