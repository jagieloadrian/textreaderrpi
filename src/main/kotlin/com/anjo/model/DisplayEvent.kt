package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class DisplayEvent(
    val id: String,
    val text: String,
    val effect: String,
    val zoneId: String?,
    val displayedAt: String
)
