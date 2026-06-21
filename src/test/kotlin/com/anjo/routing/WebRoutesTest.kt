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

class WebRoutesTest : FunSpec({

    test("GET / returns 200 with zone select and effect preview") {
        testApplication {
            application { module() }
            val response = client.get("/") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "zoneSelect"
            body shouldContain "All zones"
            body shouldContain "effectPreview"
        }
    }

    test("GET /schedule returns 200 with create form and empty schedule container") {
        testApplication {
            application { module() }
            val response = client.get("/schedule") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "scheduleListContainer"
            body shouldContain "scheduleZoneSelect"
        }
    }

    test("GET /status returns 200 with status-uptime skeleton and Loading placeholder") {
        testApplication {
            application { module() }
            val response = client.get("/status") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "status-uptime"
            body shouldContain "Loading..."
        }
    }

    test("GET / includes custom.css link and 5 nav hrefs without settings link") {
        testApplication {
            application { module() }
            val response = client.get("/") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "/static/custom.css"
            body.contains("/settings/display") shouldBe false
            body shouldContain "href=\"/zones\""
            body shouldContain "href=\"/history\""
            body shouldContain "href=\"/schedule\""
            body shouldContain "href=\"/status\""
        }
    }

    test("GET /settings/display returns 404") {
        testApplication {
            application { module() }
            val response = client.get("/settings/display") { header(HttpHeaders.Accept, ContentType.Text.Html.toString()) }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
