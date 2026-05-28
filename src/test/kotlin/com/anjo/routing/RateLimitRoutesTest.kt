package com.anjo.routing

import com.anjo.di.installApiRateLimiting
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class RateLimitRoutesTest : FunSpec({
    test("should allow requests below rate limit") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 5)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                }
            }
            repeat(5) { client.get("/api/v1/test").status shouldBe HttpStatusCode.OK }
        }
    }

    test("should return 429 with Retry-After header when rate limit exceeded") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 2)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                }
            }
            repeat(2) { client.get("/api/v1/test").status shouldBe HttpStatusCode.OK }
            val limited = client.get("/api/v1/test")
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter] shouldBe "60"
        }
    }

    test("should include error message in rate-limited response") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 1)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                }
            }
            client.get("/api/v1/test")
            val limited = client.get("/api/v1/test")
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.bodyAsText() shouldContain "Rate limit exceeded"
        }
    }

    test("should not rate-limit paths outside /api/v1") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 1)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                    get("/status") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                }
            }
            repeat(5) { client.get("/status").status shouldBe HttpStatusCode.OK }
        }
    }

    test("should rate-limit /api/v1 routes after threshold") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 2)
                        get("/display/status") { call.respond(HttpStatusCode.OK, mapOf("test" to true)) }
                    }
                }
            }
            client.get("/api/v1/display/status").status shouldBe HttpStatusCode.OK
            client.get("/api/v1/display/status").status shouldBe HttpStatusCode.OK
            client.get("/api/v1/display/status").status shouldBe HttpStatusCode.TooManyRequests
        }
    }

    test("should rate-limit POST /api/v1/text with Retry-After header") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 1)
                        post("/text") { call.respond(HttpStatusCode.Accepted, mapOf("ok" to true)) }
                    }
                }
            }
            val first = client.post("/api/v1/text") {
                header("Content-Type", "application/json")
                setBody("""{"text":"hello"}""")
            }
            first.status shouldBe HttpStatusCode.Accepted
            val limited = client.post("/api/v1/text") {
                header("Content-Type", "application/json")
                setBody("""{"text":"hello"}""")
            }
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter] shouldBe "60"
        }
    }

    test("should return 429 with Retry-After for rate-limited GET /api/v1/display/status") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        installApiRateLimiting(requestsPerMinute = 1)
                        get("/display/status") { call.respond(HttpStatusCode.OK, mapOf("test" to true)) }
                    }
                }
            }
            client.get("/api/v1/display/status").status shouldBe HttpStatusCode.OK
            val limited = client.get("/api/v1/display/status")
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter] shouldBe "60"
        }
    }
})
