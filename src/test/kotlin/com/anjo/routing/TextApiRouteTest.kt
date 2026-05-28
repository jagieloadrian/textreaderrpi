package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class TextApiRouteTest : FunSpec({

    test("should return 202 for valid text on POST /api/v1/text") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/text") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"Hello World"}""")
            }
            response.status shouldBe HttpStatusCode.Accepted
            response.bodyAsText() shouldContain "accepted"
        }
    }

    test("should return 422 for blank text on POST /api/v1/text") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/text") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":""}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.bodyAsText() shouldContain "error"
        }
    }

    test("should return 422 when text exceeds max length on POST /api/v1/text") {
        testApplication {
            application { module() }
            val longText = "a".repeat(200)
            val response = client.post("/api/v1/text") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"$longText"}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.bodyAsText() shouldContain "error"
        }
    }

    test("should return 202 for FADE effect on POST /api/v1/text") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/text") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"hello","effect":"FADE"}""")
            }
            response.status shouldBe HttpStatusCode.Accepted
        }
    }

    test("should return 202 when effect defaults to SCROLL on POST /api/v1/text") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/text") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"hello"}""")
            }
            response.status shouldBe HttpStatusCode.Accepted
        }
    }

    test("should return 4xx for invalid effect on POST /api/v1/text") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/text") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"text":"hello","effect":"KABOOM"}""")
            }
            response.status.value shouldBeInRange (400..422)
        }
    }
})

