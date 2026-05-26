package com.anjo

import com.anjo.config.ConfigLoader
import com.anjo.config.ApplicationConfig
import com.anjo.config.configureHTTP
import com.anjo.config.configureMonitoring
import com.anjo.config.configureSerialization
import com.anjo.di.configureDI
import com.anjo.routing.configureRouting
import io.ktor.server.application.*
import io.ktor.util.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Load configuration early - this must be first
    val appConfig = ConfigLoader.loadConfig(this)
    attributes.put(AttributeKey<ApplicationConfig>("appConfig"), appConfig)
    
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureDI()
    configureRouting()
}


