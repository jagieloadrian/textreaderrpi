package com.anjo.driver

import kotlinx.coroutines.CoroutineScope

data class DisplayStatus(
    val isActive: Boolean,
    val hardwareAvailable: Boolean,
    val currentMessage: String? = null,
    val error: String? = null
)

interface DisplayDriver {
    fun scrollText(scope: CoroutineScope, text: String, speedMs: Long = 16)
    fun clear()
    fun write(text: String)
    fun status(): DisplayStatus
    fun stop()
}

