package com.anjo.service

import com.anjo.config.model.DisplayConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.LcdDisplay
import com.anjo.driver.Max7219Matrix
import com.anjo.driver.OledDisplay
import com.pi4j.context.Context
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock

class DisplaySelectionService(
    private val ctx: Context,
    private val displayConfig: DisplayConfig,
    private val driverFactory: (String, Context, DisplayConfig) -> DisplayDriver? = ::defaultDriverFactory,
) {
    private var currentDriver: DisplayDriver? = null
    private var currentType: String = "UNKNOWN"
    private val displayLock = ReentrantReadWriteLock()
    private val pendingSwitches = ConcurrentLinkedQueue<String>()
    // Cache drivers by type to avoid re-registering Pi4J hardware IDs
    private val driverCache = mutableMapOf<String, DisplayDriver>()

    init {
        selectDisplayAtStartup(displayConfig.type)
    }

    private fun selectDisplayAtStartup(displayType: String) {
        val normalizedType = normalizeDisplayType(displayType)
        val driver = createDriver(normalizedType)
        if (driver != null) {
            currentDriver = driver
            currentType = normalizedType
        } else {
            System.err.println("ERROR: Failed to initialize $normalizedType display")
        }
    }

    private fun createDriver(displayType: String): DisplayDriver? {
        // Return cached driver to prevent Pi4J from rejecting duplicate hardware registrations
        driverCache[displayType]?.let { return it }
        return try {
            val driver = driverFactory(displayType, ctx, displayConfig) ?: return null
            driverCache[displayType] = driver
            driver
        } catch (e: Exception) {
            System.err.println("Failed to create $displayType driver: ${e.message}")
            null
        }
    }

    fun currentDriver(): DisplayDriver? {
        displayLock.readLock().lock()
        try {
            return currentDriver
        } finally {
            displayLock.readLock().unlock()
        }
    }

    fun selectDisplay(displayType: String): Boolean {
        displayLock.writeLock().lock()
        try {
            val normalizedType = normalizeDisplayType(displayType)
            if (normalizedType == currentType) return true
            val newDriver = createDriver(normalizedType)

            return if (newDriver != null) {
                currentDriver?.stop() // stop any ongoing animation
                currentDriver = newDriver
                currentType = normalizedType
                pendingSwitches.offer(normalizedType)
                true
            } else {
                false
            }
        } finally {
            displayLock.writeLock().unlock()
        }
    }

    fun getPendingSwitches(): List<String> = pendingSwitches.toList()

    fun clearPendingSwitches() {
        pendingSwitches.clear()
    }

    fun getCurrentDisplayType(): String {
        displayLock.readLock().lock()
        try {
            return currentType
        } finally {
            displayLock.readLock().unlock()
        }
    }

    private fun normalizeDisplayType(displayType: String): String = displayType.uppercase()

    companion object {
        private fun defaultDriverFactory(
            displayType: String,
            ctx: Context,
            config: DisplayConfig,
        ): DisplayDriver? {
            return when (displayType) {
                "MAX7219" -> Max7219Matrix(ctx, config.max7219.numDevices)
                "LCD" -> LcdDisplay(ctx, config.lcd.i2cAddress, config.lcd.busNumber)
                "OLED" -> OledDisplay(ctx, config.oled.i2cAddress, config.oled.busNumber, config.oled.width, config.oled.height)

                else -> {
                    System.err.println("Unknown display type: $displayType")
                    null
                }
            }
        }
    }
}


