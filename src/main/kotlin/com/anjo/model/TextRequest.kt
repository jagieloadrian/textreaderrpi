package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class TextRequest(
    val text: String,
    val effect: Effect = Effect.SCROLL,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.INTERRUPT,
    val speed: Int? = null,
    val blinkPeriod: Int? = null,
    val fadeSteps: Int? = null
)
