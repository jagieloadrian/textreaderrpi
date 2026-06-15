package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    val scheduleId: String,
    val text: String,
    val effect: String,
    val zoneId: String?,
    val firedAt: String
)
