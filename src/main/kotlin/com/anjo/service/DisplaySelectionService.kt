package com.anjo.service

import com.anjo.config.model.DisplayConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.Max7219Matrix
import com.anjo.driver.LcdDisplay
import com.pi4j.context.Context
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * DisplaySelectionService: Manages display driver selection and switching
 *
 * Responsibilities:
 * - Load display driver per displayConfig.type at startup (per D-03)
 * - Provide currentDriver() for rendering operations
 * - Enable runtime display queuing per D-04 (selectDisplay)
 * - Handle pendingSwitch state for deferred driver changes
 *
 * Thread-safe via ReentrantReadWriteLock for concurrent access.
 */
class DisplaySelectionService(
    private val ctx: Context,
    private val displayConfig: DisplayConfig,
) {
    private var currentDriver: DisplayDriver? = null
    private val displayLock = ReentrantReadWriteLock()
    private val pendingSwitches = ConcurrentLinkedQueue<String>()

    init {
        // Startup: Select display per D-03
        selectDisplayAtStartup(displayConfig.type)
    }

    /**
     * Select display at startup based on configuration.
     * Fail-fast if driver cannot be initialized (per D-09).
     */
    private fun selectDisplayAtStartup(displayType: String) {
        val driver = createDriver(displayType)
        if (driver != null) {
            this.currentDriver = driver
        } else {
            System.err.println("ERROR: Failed to initialize $displayType display")
            // Continue with null driver; graceful degradation
        }
    }

    /**
     * Create display driver instance based on type.
     * Returns null if hardware unavailable or type unsupported.
     */
    private fun createDriver(displayType: String): DisplayDriver? {
        return try {
            when (displayType.uppercase()) {
                "MAX7219" -> Max7219Matrix(ctx, displayConfig.max7219.numDevices)
                "LCD" -> LcdDisplay(ctx, displayConfig.lcd.i2cAddress, displayConfig.lcd.busNumber)
                "OLED" -> {
                    // OledDisplay not yet implemented
                    System.err.println("OLED support coming in Phase 2 Wave 3")
                    null
                }
                else -> {
                    System.err.println("Unknown display type: $displayType")
                    null
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to create $displayType driver: ${e.message}")
            null
        }
    }

    /**
     * Get current display driver.
     * Returns null if no driver is available (graceful degradation).
     */
    fun currentDriver(): DisplayDriver? {
        displayLock.readLock().lock()
        try {
            return currentDriver
        } finally {
            displayLock.readLock().unlock()
        }
    }

    /**
     * Queue a display type switch for later execution.
     * Per D-04: Runtime switching without server restart.
     * 
     * Returns true if queued successfully, false if driver creation failed.
     */
    fun selectDisplay(displayType: String): Boolean {
        displayLock.writeLock().lock()
        try {
            val newDriver = createDriver(displayType)
            
            return if (newDriver != null) {
                currentDriver?.stop() // Stop current driver
                currentDriver = newDriver
                pendingSwitches.offer(displayType)
                true
            } else {
                false
            }
        } finally {
            displayLock.writeLock().unlock()
        }
    }

    /**
     * Get pending display switches (for logging/debugging).
     * Returns list of queued display type changes.
     */
    fun getPendingSwitches(): List<String> {
        return pendingSwitches.toList()
    }

    /**
     * Clear pending switches after they're applied.
     */
    fun clearPendingSwitches() {
        pendingSwitches.clear()
    }

    /**
     * Get current display type (for status endpoint).
     */
    fun getCurrentDisplayType(): String {
        displayLock.readLock().lock()
        try {
            return when (currentDriver) {
                is Max7219Matrix -> "MAX7219"
                is LcdDisplay -> "LCD"
                else -> "UNKNOWN"
            }
        } finally {
            displayLock.readLock().unlock()
        }
    }
}


