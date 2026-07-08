package com.anjo.config.loader

import com.anjo.config.model.ApiConfig
import com.anjo.config.model.ApplicationConfig
import com.anjo.config.model.DatabaseConfig
import com.anjo.config.model.DisplayConfig
import com.anjo.config.model.LcdConfig
import com.anjo.config.model.Max7219Config
import com.anjo.config.model.MetricsConfig
import com.anjo.config.model.OledConfig
import com.anjo.config.model.RetryConfig
import com.anjo.config.model.WebhooksConfig
import com.anjo.config.model.ZoneConfig
import com.anjo.config.model.ZonesConfig
import com.anjo.model.DisplayType
import io.ktor.server.application.Application
import org.slf4j.LoggerFactory

object ConfigLoader {
    private val log = LoggerFactory.getLogger(ConfigLoader::class.java)

    private fun io.ktor.server.config.ApplicationConfig.int(path: String, default: Int): Int =
        propertyOrNull(path)?.getString()?.toIntOrNull() ?: default

    private fun io.ktor.server.config.ApplicationConfig.long(path: String, default: Long): Long =
        propertyOrNull(path)?.getString()?.toLongOrNull() ?: default

    private fun io.ktor.server.config.ApplicationConfig.double(path: String, default: Double): Double =
        propertyOrNull(path)?.getString()?.toDoubleOrNull() ?: default

    private fun io.ktor.server.config.ApplicationConfig.str(path: String, default: String): String =
        propertyOrNull(path)?.getString() ?: default

    fun loadConfig(application: Application): ApplicationConfig {
        val config = application.environment.config

        val displayConfig = DisplayConfig(
            type = parseDisplayType(config.str("display.type", "MAX7219")),
            max7219 = Max7219Config(
                numDevices = config.int("display.max7219.numDevices", 2),
                brightness = config.propertyOrNull("display.max7219.brightness")
                    ?.getString()
                    ?.toBooleanStrictOrNull()
                    .also { if (it == null) log.warn("display.max7219.brightness value is not a strict boolean; defaulting to true") }
                    ?: true
            ),
            lcd = LcdConfig(
                i2cAddress = config.propertyOrNull("display.lcd.i2cAddress")?.getString()?.toIntAuto() ?: 0x27,
                busNumber = config.int("display.lcd.busNumber", 1)
            ),
            oled = OledConfig(
                i2cAddress = config.propertyOrNull("display.oled.i2cAddress")?.getString()?.toIntAuto() ?: 0x3C,
                busNumber = config.int("display.oled.busNumber", 1),
                width = config.int("display.oled.width", 128),
                height = config.int("display.oled.height", 64)
            )
        )

        val apiConfig = ApiConfig(
            maxTextLength = config.int("api.maxTextLength", 128),
            rateLimitPerMinute = config.int("api.rateLimitPerMinute", 60),
            metricsRateLimitPerMinute = config.int("api.metricsRateLimitPerMinute", 120)
        )

        val metricsConfig = MetricsConfig(
            enabled = config.propertyOrNull("metrics.enabled")?.getString()?.toBooleanStrictOrNull() ?: true,
            prefix = config.str("metrics.prefix", "textreaderrpi"),
        )

        val retryConfig = RetryConfig(
            maxAttempts = config.int("retry.maxAttempts", 5),
            initialDelayMs = config.long("retry.initialDelayMs", 1000L),
            maxDelayMs = config.long("retry.maxDelayMs", 30000L),
            factor = config.double("retry.factor", 2.0),
        )

        val databaseConfig = DatabaseConfig(
            url = config.str("database.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"),
            driver = config.str("database.driver", "org.h2.Driver"),
            user = config.str("database.user", ""),
            password = config.str("database.password", ""),
            poolSize = config.int("database.poolSize", 5)
        )

        val webhooksConfig = WebhooksConfig(
            defaultUrl = config.propertyOrNull("webhooks.defaultUrl")?.getString()?.takeIf { it.isNotBlank() }
        )

        val zonesConfig = loadZonesConfig(application)
        val discoveryEnabled = (System.getProperty("discovery.enabled")
            ?: config.propertyOrNull("discovery.enabled")?.getString())
            ?.toBooleanStrictOrNull() ?: true

        return ApplicationConfig(
            display = displayConfig,
            zones = zonesConfig,
            api = apiConfig,
            metrics = metricsConfig,
            retryConfig = retryConfig,
            databaseConfig = databaseConfig,
            webhooks = webhooksConfig,
            discoveryEnabled = discoveryEnabled
        )
    }

    private fun loadZonesConfig(application: Application): ZonesConfig {
        val config = application.environment.config
        val zones = mutableListOf<ZoneConfig>()
        var index = 0
        while (true) {
            val id = config.propertyOrNull("display.zones.$index.id")?.getString() ?: break
            val type = parseDisplayType(config.str("display.zones.$index.type", "MAX7219"))
            val numDevices = config.int("display.zones.$index.numDevices", 2)
            val bus = config.int("display.zones.$index.bus", 0)
            val chipSelect = config.int("display.zones.$index.chipSelect", 0)
            zones.add(ZoneConfig(id = id, type = type, numDevices = numDevices, bus = bus, chipSelect = chipSelect))
            index++
        }
        if (zones.isEmpty()) {
            val fallbackType = parseDisplayType(config.str("display.type", "MAX7219"))
            val fallbackNumDevices = config.int("display.max7219.numDevices", 2)
            zones.add(ZoneConfig(id = "main", type = fallbackType, numDevices = fallbackNumDevices, bus = 0, chipSelect = 0))
        }
        return ZonesConfig(zones)
    }

    private fun parseDisplayType(raw: String): DisplayType {
        val result = DisplayType.fromString(raw)
        if (result == DisplayType.UNKNOWN) {
            log.warn("Unknown display type '$raw'; defaulting to MAX7219")
            return DisplayType.MAX7219
        }
        return result
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
