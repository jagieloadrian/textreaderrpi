package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

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

    test("GET /health/detail returns 404 after endpoint removal") {
        testApplication {
            application { module() }

            val response = client.get("/health/detail")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
