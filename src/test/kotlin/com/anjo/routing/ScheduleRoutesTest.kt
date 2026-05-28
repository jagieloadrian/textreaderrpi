package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ScheduleRoutesTest : FunSpec({

    test("GET /api/v1/schedule returns 200 with list") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/schedule")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "["
        }
    }

    test("POST /api/v1/schedule returns 201 for valid recurring body") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"hello","triggerType":"RECURRING","triggerValue":"5m","effect":"SCROLL","priority":0}""")
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("POST /api/v1/schedule returns 422 for text > 512 chars") {
        testApplication {
            application { module() }
            val longText = "a".repeat(513)
            val response = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"$longText","triggerType":"RECURRING","triggerValue":"5m","effect":"SCROLL","priority":0}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    test("POST /api/v1/schedule returns 422 for invalid cron") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"test","triggerType":"CRON","triggerValue":"not a cron","effect":"SCROLL","priority":0}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.bodyAsText() shouldContain "invalid cron"
        }
    }

    test("POST /api/v1/schedule returns 201 for valid cron expression") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"test","triggerType":"CRON","triggerValue":"0 * * * *","effect":"SCROLL","priority":0}""")
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("GET /api/v1/schedule/{id} returns 404 for missing id") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/schedule/nonexistent-id")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("DELETE /api/v1/schedule/{id} returns 404 for missing id") {
        testApplication {
            application { module() }
            val response = client.delete("/api/v1/schedule/nonexistent-id")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST then GET then DELETE round-trip") {
        testApplication {
            application { module() }
            val createResponse = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"roundtrip test","triggerType":"RECURRING","triggerValue":"1h","effect":"BLINK","priority":5}""")
            }
            createResponse.status shouldBe HttpStatusCode.Created
            val body = createResponse.bodyAsText()
            body shouldContain "roundtrip test"

            // Extract id from response body (simple JSON parse)
            val idMatch = Regex(""""id"\s*:\s*"([^"]+)"""").find(body)
            val id = idMatch?.groupValues?.get(1)

            if (id != null && id.isNotEmpty()) {
                val getResponse = client.get("/api/v1/schedule/$id")
                getResponse.status shouldBe HttpStatusCode.OK

                val deleteResponse = client.delete("/api/v1/schedule/$id")
                deleteResponse.status shouldBe HttpStatusCode.NoContent
            }
        }
    }
})

