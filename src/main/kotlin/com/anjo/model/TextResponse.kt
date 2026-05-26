package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class TextResponse(
    val accepted: Boolean,
    val message: String
)

