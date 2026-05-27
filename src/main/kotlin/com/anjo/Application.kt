package com.anjo

import com.anjo.di.configureDI
import com.anjo.di.configureHTTP
import com.anjo.di.configureMonitoring
import com.anjo.di.configureSerialization
import com.anjo.routing.configureErrorHandling
import com.anjo.routing.configureRequestValidation
import com.anjo.routing.configureRouting
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureSerialization()
    configureDI()
    configureMonitoring()
    configureRequestValidation()
    configureErrorHandling()
    configureRouting()
}


