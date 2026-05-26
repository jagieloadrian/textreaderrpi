package com.anjo

import com.anjo.config.keys.ApplicationConfigKey
import com.anjo.config.loader.ConfigLoader
import com.anjo.config.plugins.error.configureErrorHandling
import com.anjo.config.plugins.http.configureHTTP
import com.anjo.config.plugins.monitoring.configureMonitoring
import com.anjo.config.plugins.serialization.configureSerialization
import com.anjo.config.plugins.validation.configureRequestValidation
import com.anjo.di.configureDI
import com.anjo.routing.configureRouting
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val appConfig = ConfigLoader.loadConfig(this)
    attributes.put(ApplicationConfigKey, appConfig)

    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureDI()
    configureRequestValidation()
    configureErrorHandling()
    configureRouting()
}


