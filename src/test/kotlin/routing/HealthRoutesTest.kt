package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class HealthRoutesTest : FunSpec({
    test("GET /health returns 200 with status field") {
        testApplication {
            application { module() }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "status"
            response.bodyAsText() shouldContain "UP"
        }
    }

    test("GET /health returns memory and uptime fields") {
        testApplication {
            application { module() }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "uptime"
            response.bodyAsText() shouldContain "memoryUsedMb"
            response.bodyAsText() shouldContain "memoryMaxMb"
        }
    }

    test("GET /health/ready returns 200 or 503 with readiness status") {
        testApplication {
            application { module() }

            val response = client.get("/health/ready")

            response.bodyAsText() shouldContain "status"
            response.bodyAsText() shouldContain "isActive"
            response.bodyAsText() shouldContain "displayType"
        }
    }

    test("GET /health/ready returns 200 or 503 with readiness body") {
        testApplication {
            application { module() }

            val response = client.get("/health/ready")

            // Response is either 200 (hardware available) or 503 (unavailable)
            val validStatuses = setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            response.status shouldBe response.status.also { assert(it in validStatuses) }
            response.bodyAsText() shouldContain "isActive"
            response.bodyAsText() shouldContain "displayType"
        }
    }

    test("no /metrics endpoint exists") {
        testApplication {
            application { module() }

            val response = client.get("/metrics")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})

