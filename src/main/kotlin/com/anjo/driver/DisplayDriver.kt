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

    /** Set display brightness. level: 0 (off) to 15 (max). Default: no-op. */
    suspend fun setBrightness(level: Int) {}

    /** Display text statically without scrolling animation. Default: delegates to write(). */
    suspend fun displayStatic(text: String) { write(text) }
}

