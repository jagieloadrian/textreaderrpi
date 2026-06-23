package com.anjo.routing

import com.anjo.model.DisplayEvent
import com.anjo.module
import com.anjo.service.DisplayEventBus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.first

class LiveRoutesTest : FunSpec({

    test("GET /api/v1/live returns text/event-stream content type") {
        testApplication {
            application { module() }
            client.prepareGet("/api/v1/live").execute { response ->
                response.status shouldBe HttpStatusCode.OK
                response.headers["Content-Type"]!! shouldStartWith "text/event-stream"
            }
        }
    }

    test("GET /api/v1/live streams display event as named SSE frame") {
        val server = embeddedServer(Netty, port = 0) { module() }
        server.start(wait = false)
        val client = HttpClient(CIO) { install(SSE) }
        try {
            val port = server.engine.resolvedConnectors().first().port
            val bus = server.application.dependencies.getBlocking<DisplayEventBus>(DependencyKey<DisplayEventBus>())
            val event = DisplayEvent(id = "ev-1", text = "hello", effect = "SCROLL", zoneId = null, displayedAt = "2026-06-22T00:00:00Z")
            bus.tryEmit(event)
            var foundEvent = false
            var foundData = false
            client.sse("http://localhost:$port/api/v1/live") {
                val frame = incoming.first()
                foundEvent = frame.event == "display"
                foundData = frame.data?.contains("\"hello\"") == true
            }
            foundEvent shouldBe true
            foundData shouldBe true
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }
})
