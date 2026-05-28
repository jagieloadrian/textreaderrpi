package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ScheduleRoutesTest : FunSpec({

    test("should return 200 with schedule list for GET /api/v1/schedule") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/schedule")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "["
        }
    }

    test("should return 201 when creating valid recurring schedule") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"hello","triggerType":"RECURRING","triggerValue":"5m","effect":"SCROLL","priority":0}""")
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("should return 422 when text exceeds 512 characters") {
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

    test("should return 422 with error for invalid cron expression") {
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

    test("should return 201 for valid cron expression") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"test","triggerType":"CRON","triggerValue":"0 * * * *","effect":"SCROLL","priority":0}""")
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("should return 404 for unknown schedule id on GET") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/schedule/nonexistent-id")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("should return 404 when deleting unknown schedule") {
        testApplication {
            application { module() }
            val response = client.delete("/api/v1/schedule/nonexistent-id")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("should return 204 when cancelling active recurring schedule") {
        testApplication {
            application { module() }
            val createResponse = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"cancel test","triggerType":"RECURRING","triggerValue":"1h","effect":"SCROLL","priority":0}""")
            }
            createResponse.status shouldBe HttpStatusCode.Created
            val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(createResponse.bodyAsText())?.groupValues?.get(1)

            requireNotNull(id) { "id missing from create response" }
            val cancelResponse = client.post("/api/v1/schedule/$id/cancel")
            cancelResponse.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("should return 204 idempotently for cancel on unknown id") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/schedule/nonexistent-id/cancel")
            response.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("should complete POST GET DELETE round-trip successfully") {
        testApplication {
            application { module() }
            val createResponse = client.post("/api/v1/schedule") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"roundtrip test","triggerType":"RECURRING","triggerValue":"1h","effect":"BLINK","priority":5}""")
            }
            createResponse.status shouldBe HttpStatusCode.Created
            val body = createResponse.bodyAsText()
            body shouldContain "roundtrip test"

            val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            if (id != null && id.isNotEmpty()) {
                client.get("/api/v1/schedule/$id").status shouldBe HttpStatusCode.OK
                client.delete("/api/v1/schedule/$id").status shouldBe HttpStatusCode.NoContent
            }
        }
    }
})
