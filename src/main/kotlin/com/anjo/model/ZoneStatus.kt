package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class ZoneStatus(
    val id: String,
    val type: String,
    val status: String,
    val ip: String? = null,
    val lastSeenAt: String? = null
)
