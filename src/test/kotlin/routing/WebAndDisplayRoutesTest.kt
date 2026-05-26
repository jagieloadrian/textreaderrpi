package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class WebAndDisplayRoutesTest : FunSpec({
    test("GET / returns HTML page") {
        testApplication {
            application { module() }

            val response = client.get("/") {
                header(HttpHeaders.Accept, ContentType.Text.Html.toString())
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Send Text"
            response.bodyAsText() shouldContain "charCounter"
        }
    }

    test("GET /status returns status HTML") {
        testApplication {
            application { module() }

            val response = client.get("/status") {
                header(HttpHeaders.Accept, ContentType.Text.Html.toString())
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Display Status"
        }
    }

    test("GET /settings/display returns settings HTML") {
        testApplication {
            application { module() }

            val response = client.get("/settings/display") {
                header(HttpHeaders.Accept, ContentType.Text.Html.toString())
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Display Settings"
            response.bodyAsText() shouldContain "applyDriverBtn"
        }
    }

    test("GET /api/display/status returns json") {
        testApplication {
            application { module() }

            val response = client.get("/api/display/status")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "displayType"
            response.bodyAsText() shouldContain "hardwareAvailable"
        }
    }

    test("POST /api/display/select rejects invalid type") {
        testApplication {
            application { module() }

            val response = client.post("/api/display/select") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"type":"invalid-driver"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain "Unsupported driver type"
        }
    }

})

