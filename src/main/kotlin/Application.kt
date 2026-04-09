package com.anjo

import com.anjo.config.configureHTTP
import com.anjo.config.configureMonitoring
import com.anjo.config.configureSerialization
import com.anjo.di.configureFrameworks
import com.anjo.routing.configureRouting
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureFrameworks()
    configureRouting()
}
