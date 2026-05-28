package com.anjo.driver

import kotlinx.coroutines.CoroutineScope

/** Fallback driver used when no physical display is available (dev/test environments). */
object OfflineDisplayDriver : DisplayDriver {
    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) = Unit
    override fun clear() = Unit
    override fun write(text: String) = Unit
    override fun status(): DisplayStatus = DisplayStatus(
        isActive = false,
        hardwareAvailable = false,
        error = "No display driver available"
    )
    override fun stop() = Unit
}

