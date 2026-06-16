package com.anjo.service

import com.anjo.config.model.DisplayConfig
import com.anjo.driver.DisplayDriver
import com.anjo.driver.LcdDisplay
import com.anjo.driver.Max7219Matrix
import com.anjo.driver.OledDisplay
import com.pi4j.context.Context
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.slf4j.LoggerFactory

class DisplaySelectionService(
    private val ctx: Context,
    private val displayConfig: DisplayConfig,
    private val driverFactory: (String, Context, DisplayConfig) -> DisplayDriver? = ::defaultDriverFactory,
) {
    private var currentDriver: DisplayDriver? = null
    private var currentType: String = "UNKNOWN"
    private val displayLock = ReentrantReadWriteLock()
    private val driverCache = mutableMapOf<String, DisplayDriver>()
    private val log = LoggerFactory.getLogger(DisplaySelectionService::class.java)

    init {
        selectDisplayAtStartup(displayConfig.type.name)
    }

    private fun selectDisplayAtStartup(displayType: String) {
        val normalizedType = normalizeDisplayType(displayType)
        val driver = createDriver(normalizedType)
        if (driver != null) {
            currentDriver = driver
            currentType = normalizedType
            log.info("Display initialised: type=$normalizedType")
        } else {
            log.error("Failed to initialize display driver: type=$normalizedType")
        }
    }

    private fun createDriver(displayType: String): DisplayDriver? {
        driverCache[displayType]?.let { return it }
        return try {
            val driver = driverFactory(displayType, ctx, displayConfig) ?: return null
            driverCache[displayType] = driver
            log.debug("Driver created and cached: type=$displayType")
            driver
        } catch (e: Exception) {
            log.error("Failed to create driver type=$displayType: ${e.message}", e)
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
            if (normalizedType == currentType) {
                log.debug("Driver switch skipped: already using $normalizedType")
                return true
            }
            val newDriver = createDriver(normalizedType)

            return if (newDriver != null) {
                currentDriver?.stop()
                currentDriver = newDriver
                currentType = normalizedType
                log.info("Driver switched: $normalizedType")
                true
            } else {
                log.warn("Driver switch failed: could not create driver for $normalizedType")
                false
            }
        } finally {
            displayLock.writeLock().unlock()
        }
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
        private val factoryLog = LoggerFactory.getLogger(DisplaySelectionService::class.java)

        private fun defaultDriverFactory(
            displayType: String,
            ctx: Context,
            config: DisplayConfig,
        ): DisplayDriver? {
            return when (displayType) {
                "MAX7219" -> Max7219Matrix(ctx, config.max7219.numDevices, zoneId = 0)
                "LCD" -> LcdDisplay(ctx, config.lcd.i2cAddress, config.lcd.busNumber)
                "OLED" -> OledDisplay(ctx, config.oled.i2cAddress, config.oled.busNumber, config.oled.width, config.oled.height)

                else -> {
                    factoryLog.warn("Unknown display type requested: $displayType")
                    null
                }
            }
        }
    }
}
