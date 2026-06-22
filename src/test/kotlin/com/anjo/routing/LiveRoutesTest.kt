package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

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
})
