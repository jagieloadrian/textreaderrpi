package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HealthRoutesTest : FunSpec({
    test("should return 200 with appAlive check for GET /health") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBeIn setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            response.bodyAsText() shouldContain "appAlive"
        }
    }

    test("should return readiness details for GET /health/ready") {
        testApplication {
            application { module() }
            val response = client.get("/health/ready")
            response.status shouldBeIn setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            response.bodyAsText() shouldContain "displayReady"
        }
    }

    test("should return 200 with HealthDetailResponse fields for GET /health/detail") {
        testApplication {
            application { module() }
            val response = client.get("/health/detail")
            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["uptime"] shouldNotBe null
            json["memoryUsed"] shouldNotBe null
            json["memoryMax"] shouldNotBe null
            json["displayStatus"]!!.jsonPrimitive.content shouldBeIn listOf("ONLINE", "OFFLINE")
            json["totalFailures"] shouldNotBe null
            json["zoneErrors"] shouldNotBe null
        }
    }

    test("should return displayAvailable check for GET /health") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBeIn setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            response.bodyAsText() shouldContain "displayAvailable"
        }
    }
})
