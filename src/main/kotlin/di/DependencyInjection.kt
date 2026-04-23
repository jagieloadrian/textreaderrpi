package com.anjo.di

import com.anjo.driver.Max7219Matrix
import com.anjo.service.ReaderInputService
import com.anjo.service.ScreenDriverService
import com.pi4j.Pi4J
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    dependencies {
        provide { Dispatchers.IO }
        provide { ScreenDriverService(get(DependencyKey<Max7219Matrix>()), get(DependencyKey<CoroutineDispatcher>()) ) }
        provide { ReaderInputService(get(DependencyKey<ScreenDriverService>())) }
        provide { Max7219Matrix(ctx = Pi4J.newAutoContext()) }
    }
}
