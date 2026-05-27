package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class HealthRoutesTest : FunSpec({
    test("GET /health returns 200 with KHealth appAlive check") {
        testApplication {
            application { module() }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "appAlive"
        }
    }

    test("GET /health/ready returns readiness check details") {
        testApplication {
            application { module() }

            val response = client.get("/health/ready")

            val validStatuses = setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            response.status shouldBe response.status.also { assert(it in validStatuses) }
            response.bodyAsText() shouldContain "displayReady"
        }
    }

    test("GET /health/detail returns extended health payload with all fields") {
        testApplication {
            application { module() }

            val response = client.get("/health/detail")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject

            // All 7 fields are present
            json.containsKey("status") shouldBe true
            json.containsKey("uptime") shouldBe true
            json.containsKey("memoryUsedMb") shouldBe true
            json.containsKey("memoryMaxMb") shouldBe true
            json.containsKey("displayType") shouldBe true
            json.containsKey("isActive") shouldBe true
            json.containsKey("lastError") shouldBe true

            // uptime > 0
            json["uptime"]!!.jsonPrimitive.long shouldBeGreaterThan 0L
            // memoryUsedMb > 0
            json["memoryUsedMb"]!!.jsonPrimitive.long shouldBeGreaterThan 0L
            // displayType is non-empty
            json["displayType"]!!.jsonPrimitive.content.isNotEmpty() shouldBe true
        }
    }
})

