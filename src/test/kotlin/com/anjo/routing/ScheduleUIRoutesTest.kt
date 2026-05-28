package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ScheduleUIRoutesTest : FunSpec({

    test("should return 200 HTML for GET /schedule") {
        testApplication {
            application { module() }
            val response = client.get("/schedule") {
                header(HttpHeaders.Accept, ContentType.Text.Html.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentType] shouldContain "text/html"
        }
    }

    test("should contain Schedule Manager heading on /schedule page") {
        testApplication {
            application { module() }
            val response = client.get("/schedule")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Schedule Manager"
        }
    }

    test("should contain table headers Text and Status on /schedule page") {
        testApplication {
            application { module() }
            val response = client.get("/schedule")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Text"
            response.bodyAsText() shouldContain "Status"
        }
    }
})
