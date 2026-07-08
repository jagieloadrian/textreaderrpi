package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class NetworkZone(
    val id: String,
    val name: String,
    val ip: String? = null,
    val type: String = "MAX7219",
    val discoveryMethod: String,
    val createdAt: String,
    val lastSeenAt: String? = null,
    val displaySubtype: String? = null
)
