package com.anjo

import com.anjo.config.ConfigLoader
import com.anjo.config.ApplicationConfigKey
import com.anjo.config.configureErrorHandling
import com.anjo.config.configureHTTP
import com.anjo.config.configureMonitoring
import com.anjo.config.configureRequestValidation
import com.anjo.config.configureSerialization
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


