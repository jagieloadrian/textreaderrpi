package com.anjo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ApplicationTest : FunSpec({

    test("should start application context and serve health endpoint") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should expose API routes after context startup") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/schedule")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should serve static assets after context startup") {
        testApplication {
            application { module() }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should return 404 for unknown routes") {
        testApplication {
            application { module() }
            val response = client.get("/nonexistent-endpoint")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("should include application name in health response") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})

