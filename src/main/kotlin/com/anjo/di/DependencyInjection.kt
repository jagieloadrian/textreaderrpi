package com.anjo.di

import com.anjo.config.loader.ConfigLoader
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.service.DisplaySelectionService
import com.anjo.service.MetricsCollector
import com.anjo.service.ReaderInputService
import com.anjo.service.RetryConfig
import com.anjo.service.ResourceTracker
import com.anjo.service.ScreenDriverService
import com.codahale.metrics.MetricRegistry
import com.pi4j.Pi4J
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    val config = environment.config
    val pi4jContext = Pi4J.newAutoContext()

    val displaySelectionService = DisplaySelectionService(
        ctx = pi4jContext,
        displayConfig = appConfig.display
    )

    val driver: DisplayDriver? = displaySelectionService.currentDriver()
    val retryConfig = RetryConfig(
        maxAttempts = config.propertyOrNull("retry.maxAttempts")?.getString()?.toIntOrNull() ?: 5,
        initialDelayMs = config.propertyOrNull("retry.initialDelayMs")?.getString()?.toLongOrNull() ?: 1000L,
        maxDelayMs = config.propertyOrNull("retry.maxDelayMs")?.getString()?.toLongOrNull() ?: 30000L,
        factor = config.propertyOrNull("retry.factor")?.getString()?.toDoubleOrNull() ?: 2.0,
    )
    val metricRegistry = MetricRegistry()
    val resourceTracker = ResourceTracker(
        maxSlots = 10,
        trackerName = "screenDriver",
        metricRegistry = metricRegistry,
    )

    val screenDriverService = if (driver == null) {
        ScreenDriverService(OfflineDisplayDriver, Dispatchers.IO, displaySelectionService, retryConfig, metricRegistry)
    } else {
        ScreenDriverService(driver, Dispatchers.IO, displaySelectionService, retryConfig, metricRegistry)
    }

    val readerInputService = ReaderInputService(screenDriverService)
    val metricsCollector = MetricsCollector(metricRegistry, resourceTracker)

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { appConfig.display }
        provide { Dispatchers.IO }
        provide { metricRegistry }
        provide { resourceTracker }
        provide { displaySelectionService }
        provide { screenDriverService }
        provide { readerInputService }
        provide { metricsCollector }
    }
}

private object OfflineDisplayDriver : DisplayDriver {
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
