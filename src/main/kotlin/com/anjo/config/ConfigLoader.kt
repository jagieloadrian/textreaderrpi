package com.anjo.config

import io.ktor.server.application.*

object ConfigLoader {
    fun loadConfig(application: Application): ApplicationConfig {
        // Load from application.conf (which comes from application.yaml via Ktor's config system)
        val config = application.environment.config
        
        // Parse each section and create typed objects
        val displayConfig = DisplayConfig(
            gpioPins = mapOf(
                "spi_ce" to (config.propertyOrNull("display.gpioPins.spi_ce")?.getString()?.toIntOrNull() ?: 8),
                "spi_mosi" to (config.propertyOrNull("display.gpioPins.spi_mosi")?.getString()?.toIntOrNull() ?: 10),
                "spi_miso" to (config.propertyOrNull("display.gpioPins.spi_miso")?.getString()?.toIntOrNull() ?: 9),
                "spi_sck" to (config.propertyOrNull("display.gpioPins.spi_sck")?.getString()?.toIntOrNull() ?: 11)
            ),
            numDevices = config.propertyOrNull("display.numDevices")?.getString()?.toIntOrNull() ?: 2,
            brightness = config.propertyOrNull("display.brightness")?.getString()?.toBoolean() ?: true
        )
        
        val hardwareConfig = HardwareConfig(
            spiTimeoutMs = config.propertyOrNull("hardware.spiTimeoutMs")?.getString()?.toLongOrNull() ?: 1000L,
            gpioTimeoutMs = config.propertyOrNull("hardware.gpioTimeoutMs")?.getString()?.toLongOrNull() ?: 500L
        )
        
        val apiConfig = ApiConfig(
            maxTextLength = config.propertyOrNull("api.maxTextLength")?.getString()?.toIntOrNull() ?: 128,
            queueSize = config.propertyOrNull("api.queueSize")?.getString()?.toIntOrNull() ?: 10,
            rateLimitPerMinute = config.propertyOrNull("api.rateLimitPerMinute")?.getString()?.toIntOrNull() ?: 60
        )
        
        val timingConfig = TimingConfig(
            scrollSpeed = config.propertyOrNull("timing.scrollSpeed")?.getString()?.toLongOrNull() ?: 16L,
            refreshRate = config.propertyOrNull("timing.refreshRate")?.getString()?.toIntOrNull() ?: 60
        )
        
        val loggingConfig = LoggingConfig(
            level = config.propertyOrNull("logging.level")?.getString() ?: "INFO",
            format = config.propertyOrNull("logging.format")?.getString() ?: "json"
        )
        
        return ApplicationConfig(
            display = displayConfig,
            hardware = hardwareConfig,
            api = apiConfig,
            timing = timingConfig,
            logging = loggingConfig
        )
    }
}

