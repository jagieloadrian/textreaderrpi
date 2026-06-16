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

class ZoneRoutesTest : FunSpec({

    test("GET /api/v1/zones returns 200 with JSON array") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/zones")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "["
        }
    }

    test("POST /api/v1/zones/discover returns 200 with JSON array") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/zones/discover")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "["
        }
    }

    test("POST /api/v1/zones with a public IP returns 422") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/zones") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"ip":"8.8.8.8"}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    test("POST /api/v1/zones with a valid private IP returns 201") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/zones") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"ip":"192.168.1.50"}""")
            }
            response.status.value shouldBe 201
        }
    }

    test("POST /api/v1/zones for a duplicate IP returns 409") {
        testApplication {
            application { module() }
            client.post("/api/v1/zones") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"ip":"192.168.1.99"}""")
            }
            val response = client.post("/api/v1/zones") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"ip":"192.168.1.99"}""")
            }
            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("POST /api/v1/zones with an invalid IP format returns 422") {
        testApplication {
            application { module() }
            val response = client.post("/api/v1/zones") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"ip":"not-an-ip"}""")
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }
})
