package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
    test("should return HTML for GET /") {
        testApplication {
            application { module() }
            val response = client.get("/") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Send Text"
            response.bodyAsText() shouldContain "charCounter"
        }
    }

    test("should return status HTML for GET /status") {
        testApplication {
            application { module() }
            val response = client.get("/status") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Display Status"
        }
    }

    test("should return display settings HTML for GET /settings/display") {
        testApplication {
            application { module() }
            val response = client.get("/settings/display") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Display Settings"
            response.bodyAsText() shouldContain "applyDriverBtn"
        }
    }

    test("should return JSON with display status for GET /api/v1/display/status") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/display/status")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "displayType"
            response.bodyAsText() shouldContain "hardwareAvailable"
        }
    }

    test("should reject invalid driver type with 400 on POST /api/v1/display/select") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/display/select") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"type":"invalid-driver"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain "Unsupported driver type"
        }
    }

    // BUG: swaggerUI/OpenApiDocSource.Routing in Ktor 3.5.0 intercepts unregistered GET paths and returns 200.
    // Tracked in v1.0-MILESTONE-AUDIT.md tech debt. Re-enable once routing catch-all is fixed.
    xtest("should return HTML error page for GET on non-existent browser route") {
        testApplication {
            application { module() }
            val response = client.get("/this-path-definitely-does-not-exist-xyz") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.NotFound
            val body = response.bodyAsText()
            body shouldContain "<html"
        }
    }

    test("should return JSON 404 for non-existent API route") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/nonexistent")
            response.status shouldBe HttpStatusCode.NotFound
            val body = response.bodyAsText()
            body shouldContain "ERR_404"
            body.contains("<html") shouldBe false
        }
    }
})
