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

    test("should filter history by zone for GET /history?zone=X") {
        testApplication {
            application { module() }
            val response = client.get("/history?zone=ALL") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "All zones"
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

    test("should reject invalid driver type with 422 on POST /api/v1/display/select") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/display/select") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"type":"invalid-driver"}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.bodyAsText() shouldContain "Unsupported driver type"
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
