package com.anjo.config.loader

import com.anjo.config.model.ApiConfig
import com.anjo.config.model.ApplicationConfig
import com.anjo.config.model.DatabaseConfig
import com.anjo.config.model.DisplayConfig
import com.anjo.config.model.HardwareConfig
import com.anjo.config.model.LcdConfig
import com.anjo.config.model.LoggingConfig
import com.anjo.config.model.Max7219Config
import com.anjo.config.model.MetricsConfig
import com.anjo.config.model.OledConfig
import com.anjo.config.model.RetryConfig
import com.anjo.config.model.TimingConfig
import io.ktor.server.application.Application

object ConfigLoader {
    fun loadConfig(application: Application): ApplicationConfig {
        val config = application.environment.config

        val displayConfig = DisplayConfig(
            type = config.propertyOrNull("display.type")?.getString() ?: "MAX7219",
            max7219 = Max7219Config(
                numDevices = config.propertyOrNull("display.max7219.numDevices")?.getString()?.toIntOrNull() ?: 2,
                brightness = config.propertyOrNull("display.max7219.brightness")?.getString()?.toBoolean() ?: true,
                gpioPins = mapOf(
                    "spi_ce" to (config.propertyOrNull("display.max7219.gpioPins.spi_ce")?.getString()?.toIntOrNull() ?: 8),
                    "spi_mosi" to (config.propertyOrNull("display.max7219.gpioPins.spi_mosi")?.getString()?.toIntOrNull() ?: 10),
                    "spi_miso" to (config.propertyOrNull("display.max7219.gpioPins.spi_miso")?.getString()?.toIntOrNull() ?: 9),
                    "spi_sck" to (config.propertyOrNull("display.max7219.gpioPins.spi_sck")?.getString()?.toIntOrNull() ?: 11)
                )
            ),
            lcd = LcdConfig(
                i2cAddress = config.propertyOrNull("display.lcd.i2cAddress")?.getString()?.toIntAuto() ?: 0x27,
                busNumber = config.propertyOrNull("display.lcd.busNumber")?.getString()?.toIntOrNull() ?: 1,
                rows = config.propertyOrNull("display.lcd.rows")?.getString()?.toIntOrNull() ?: 2,
                columns = config.propertyOrNull("display.lcd.columns")?.getString()?.toIntOrNull() ?: 16
            ),
            oled = OledConfig(
                i2cAddress = config.propertyOrNull("display.oled.i2cAddress")?.getString()?.toIntAuto() ?: 0x3C,
                busNumber = config.propertyOrNull("display.oled.busNumber")?.getString()?.toIntOrNull() ?: 1,
                width = config.propertyOrNull("display.oled.width")?.getString()?.toIntOrNull() ?: 128,
                height = config.propertyOrNull("display.oled.height")?.getString()?.toIntOrNull() ?: 64
            )
        )

        val hardwareConfig = HardwareConfig(
            spiTimeoutMs = config.propertyOrNull("hardware.spiTimeoutMs")?.getString()?.toLongOrNull() ?: 1000L,
            gpioTimeoutMs = config.propertyOrNull("hardware.gpioTimeoutMs")?.getString()?.toLongOrNull() ?: 500L
        )

        val apiConfig = ApiConfig(
            maxTextLength = config.propertyOrNull("api.maxTextLength")?.getString()?.toIntOrNull() ?: 128,
            queueSize = config.propertyOrNull("api.queueSize")?.getString()?.toIntOrNull() ?: 10,
            rateLimitPerMinute = config.propertyOrNull("api.rateLimitPerMinute")?.getString()?.toIntOrNull() ?: 60,
            metricsRateLimitPerMinute = config.propertyOrNull("api.metricsRateLimitPerMinute")?.getString()?.toIntOrNull() ?: 120
        )

        val timingConfig = TimingConfig(
            scrollSpeed = config.propertyOrNull("timing.scrollSpeed")?.getString()?.toLongOrNull() ?: 16L,
            refreshRate = config.propertyOrNull("timing.refreshRate")?.getString()?.toIntOrNull() ?: 60
        )

        val loggingConfig = LoggingConfig(
            level = config.propertyOrNull("logging.level")?.getString() ?: "INFO",
            format = config.propertyOrNull("logging.format")?.getString() ?: "json"
        )

        val metricsConfig = MetricsConfig(
            enabled = config.propertyOrNull("metrics.enabled")?.getString()?.toBoolean() ?: true,
            prefix = config.propertyOrNull("metrics.prefix")?.getString() ?: "textreaderrpi",
        )

        val retryConfig = RetryConfig(
            maxAttempts = config.propertyOrNull("retry.maxAttempts")?.getString()?.toIntOrNull() ?: 5,
            initialDelayMs = config.propertyOrNull("retry.initialDelayMs")?.getString()?.toLongOrNull() ?: 1000L,
            maxDelayMs = config.propertyOrNull("retry.maxDelayMs")?.getString()?.toLongOrNull() ?: 30000L,
            factor = config.propertyOrNull("retry.factor")?.getString()?.toDoubleOrNull() ?: 2.0,
        )

        val databaseConfig = DatabaseConfig(
            url = config.propertyOrNull("database.url")?.getString() ?: "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            driver = config.propertyOrNull("database.driver")?.getString() ?: "org.h2.Driver",
            user = config.propertyOrNull("database.user")?.getString() ?: "",
            password = config.propertyOrNull("database.password")?.getString() ?: "",
            poolSize = config.propertyOrNull("database.poolSize")?.getString()?.toIntOrNull() ?: 5
        )
        
        return ApplicationConfig(
            display = displayConfig,
            hardware = hardwareConfig,
            api = apiConfig,
            timing = timingConfig,
            logging = loggingConfig,
            metrics = metricsConfig,
            retryConfig = retryConfig,
            databaseConfig
        )
    }

    private fun String.toIntAuto(): Int? {
        val normalized = trim()
        return if (normalized.startsWith("0x", ignoreCase = true)) {
            normalized.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
        } else {
            normalized.toIntOrNull()
        }
    }
}

