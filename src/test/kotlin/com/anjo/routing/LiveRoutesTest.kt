package com.anjo.routing

import com.anjo.model.DisplayEvent
import com.anjo.module
import com.anjo.service.DisplayEventBus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readLine

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
        testApplication {
            application { module() }
            client.get("/health")
            val bus = application.dependencies.getBlocking<DisplayEventBus>(DependencyKey<DisplayEventBus>())
            val event = DisplayEvent(id = "ev-1", text = "hello", effect = "SCROLL", zoneId = null, displayedAt = "2026-06-22T00:00:00Z")
            bus.tryEmit(event)
            client.prepareGet("/api/v1/live").execute { response ->
                val channel = response.bodyAsChannel()
                val lines = mutableListOf<String>()
                repeat(4) {
                    val line = channel.readLine() ?: return@repeat
                    lines.add(line)
                }
                lines.any { it == "event: display" } shouldBe true
                lines.any { it.startsWith("data:") && it.contains("\"hello\"") } shouldBe true
            }
        }
    }
})
