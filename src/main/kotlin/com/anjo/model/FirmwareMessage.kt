package com.anjo.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FirmwareMessage(
    val text: String,
    val effect: String,
    val zoneId: String,
    val ts: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val speed: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val blinkPeriod: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fadeSteps: Int? = null
)
