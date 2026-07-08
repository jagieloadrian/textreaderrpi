package com.anjo.di

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.sse.SSE
import io.ktor.server.websocket.WebSockets

fun Application.configureHTTP() {
    install(CORS) {
        allowHost("localhost")
        allowHost("raspberrypi.local")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
    }
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    install(SSE)
    install(WebSockets)
}
