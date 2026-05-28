package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class TextRequest(
    val text: String,
    val effect: Effect = Effect.SCROLL
)

