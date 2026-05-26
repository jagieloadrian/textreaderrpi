package com.anjo.di

import com.anjo.config.keys.ApplicationConfigKey
import com.anjo.config.keys.ReaderInputServiceKey
import com.anjo.driver.DisplayDriver
import com.anjo.driver.Max7219Matrix
import com.anjo.service.ReaderInputService
import com.anjo.service.ScreenDriverService
import com.pi4j.Pi4J
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = attributes[ApplicationConfigKey]
    val driver: DisplayDriver = Max7219Matrix(
        ctx = Pi4J.newAutoContext(),
        numDevices = appConfig.display.numDevices
    )
    val screenDriverService = ScreenDriverService(driver, Dispatchers.IO)
    val readerInputService = ReaderInputService(screenDriverService)

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { Dispatchers.IO }
        provide { driver }
        provide { screenDriverService }
        provide { readerInputService }
    }

    attributes.put(ReaderInputServiceKey, readerInputService)
}
