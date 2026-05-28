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

    test("GET /schedule returns 200 with HTML content type") {
        testApplication {
            application { module() }
            val response = client.get("/schedule") {
                header(HttpHeaders.Accept, ContentType.Text.Html.toString())
            }
            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentType] shouldContain "text/html"
        }
    }

    test("GET /schedule page contains Schedule Manager heading") {
        testApplication {
            application { module() }
            val response = client.get("/schedule")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Schedule Manager"
        }
    }

    test("GET /schedule page contains schedule table headers") {
        testApplication {
            application { module() }
            val response = client.get("/schedule")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Text"
            response.bodyAsText() shouldContain "Status"
        }
    }
})

