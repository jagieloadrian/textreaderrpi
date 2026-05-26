package com.anjo.di

import com.anjo.config.loader.ConfigLoader
import com.anjo.driver.DisplayDriver
import com.anjo.service.DisplaySelectionService
import com.anjo.service.ReaderInputService
import com.anjo.service.ScreenDriverService
import com.pi4j.Pi4J
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    
    // Create Pi4J context once
    val pi4jContext = Pi4J.newAutoContext()
    
    // Create DisplaySelectionService: selects driver per config.display.type (D-03)
    val displaySelectionService = DisplaySelectionService(
        ctx = pi4jContext,
        displayConfig = appConfig.display
    )
    
    // Get the current driver from selection service
    // May be null if hardware unavailable (graceful degradation per D-09)
    val driver: DisplayDriver? = displaySelectionService.currentDriver()
    
    // Create services
    val screenDriverService = if (driver != null) {
        ScreenDriverService(driver, Dispatchers.IO)
    } else {
        // Offline mode: driver is null, ScreenDriverService handles it gracefully
        ScreenDriverService(
            // Create a no-op driver for offline mode
            object : DisplayDriver {
                override fun scrollText(scope: kotlinx.coroutines.CoroutineScope, text: String, speedMs: Long) {}
                override fun clear() {}
                override fun write(text: String) {}
                override fun status() = com.anjo.driver.DisplayStatus(
                    isActive = false,
                    hardwareAvailable = false,
                    currentMessage = null,
                    error = "No display driver available"
                )
                override fun stop() {}
            },
            Dispatchers.IO
        )
    }
    
    val readerInputService = ReaderInputService(screenDriverService)

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { appConfig.display }
        provide { Dispatchers.IO }
        provide { displaySelectionService }
        provide { screenDriverService }
        provide { readerInputService }
    }
}
