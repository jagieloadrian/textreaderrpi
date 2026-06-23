package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class AddZoneRequest(
    val name: String,
    val type: String,
    val ip: String? = null,
    val displaySubtype: String? = null
)
