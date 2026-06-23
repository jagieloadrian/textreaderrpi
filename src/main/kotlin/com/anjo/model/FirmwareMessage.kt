package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class FirmwareMessage(
    val text: String,
    val effect: String,
    val zoneId: String,
    val ts: String
)
