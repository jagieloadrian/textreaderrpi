package com.anjo.driver

import kotlinx.coroutines.CoroutineScope

/**
 * DisplayStatus represents the current state of a display device.
 * Used by status() method to report hardware health and operational state.
 */
data class DisplayStatus(
    val isActive: Boolean,
    val hardwareAvailable: Boolean,
    val currentMessage: String? = null,
    val error: String? = null
)

/**
 * DisplayDriver interface: Hybrid contract supporting both legacy scroll-based rendering
 * (scrollText) and new write-based static text rendering (clear/write).
 *
 * Implementations must support all methods:
 * - scrollText(scope, text, speedMs): Asynchronous scrolling text (legacy, backward compat)
 * - clear(): Clear display without content
 * - write(text): Static text display
 * - status(): Report hardware state
 * - stop(): Cancel any running operations
 *
 * Per Design Decision D-01 & D-02: DisplayDriver enables uniform control across MAX7219, LCD, OLED.
 */
interface DisplayDriver {
    /**
     * Asynchronously display scrolling text.
     * Maintains backward compatibility with MAX7219-style scroll rendering.
     */
    fun scrollText(scope: CoroutineScope, text: String, speedMs: Long = 16)

    /**
     * Clear the display (blank without content).
     * Implementation should achieve this in <2s per D-05.
     */
    fun clear()

    /**
     * Display static text on the display.
     * Implementation may center or left-align based on device capabilities.
     */
    fun write(text: String)

    /**
     * Return the current status of the display device.
     * Used by /api/display/status endpoint and health checks.
     */
    fun status(): DisplayStatus

    /**
     * Stop any running operations (scrolling, rendering).
     * Called on shutdown or display switching.
     */
    fun stop()
}

