package com.anjo.di

import com.anjo.config.loader.ConfigLoader
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.service.DisplaySelectionService
import com.anjo.service.HealthService
import com.anjo.service.RecoveryPolicy
import com.anjo.service.ReaderInputService
import com.anjo.service.ResourceTracker
import com.anjo.service.ScreenDriverService
import com.pi4j.Pi4J
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    val pi4jContext = Pi4J.newAutoContext()

    val displaySelectionService = DisplaySelectionService(
        ctx = pi4jContext,
        displayConfig = appConfig.display
    )

    val driver: DisplayDriver? = displaySelectionService.currentDriver()
    val recoveryPolicy = RecoveryPolicy()

    val screenDriverService = if (driver == null) {
        ScreenDriverService(OfflineDisplayDriver, Dispatchers.IO, displaySelectionService, recoveryPolicy)
    } else {
        ScreenDriverService(driver, Dispatchers.IO, displaySelectionService, recoveryPolicy)
    }

    val readerInputService = ReaderInputService(screenDriverService)
    val healthService = HealthService(screenDriverService)
    val rateLimiter = RateLimiter(requestsPerMinute = appConfig.api.rateLimitPerMinute)

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { appConfig.display }
        provide { Dispatchers.IO }
        provide { displaySelectionService }
        provide { screenDriverService }
        provide { readerInputService }
        provide { healthService }
        provide { rateLimiter }
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

