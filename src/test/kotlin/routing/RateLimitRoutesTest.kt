package com.anjo.routing

import com.anjo.di.installApiRateLimiting
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class RateLimitRoutesTest : FunSpec({
    test("requests below limit are allowed through") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api") {
                        installApiRateLimiting(requestsPerMinute = 5)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                }
            }
            repeat(5) {
                client.get("/api/test").status shouldBe HttpStatusCode.OK
            }
        }
    }

    test("requests over limit receive 429 with Retry-After header") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api") {
                        installApiRateLimiting(requestsPerMinute = 2)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                }
            }

            repeat(2) {
                client.get("/api/test").status shouldBe HttpStatusCode.OK
            }

            val limited = client.get("/api/test")
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter] shouldBe "60"
        }
    }

    test("rate-limited response contains error message") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api") {
                        installApiRateLimiting(requestsPerMinute = 1)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                }
            }

            client.get("/api/test")
            val limited = client.get("/api/test")

            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.bodyAsText() shouldContain "Rate limit exceeded"
        }
    }

    test("paths not matching /api are not rate-limited") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api") {
                        installApiRateLimiting(requestsPerMinute = 1)
                        get("/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                    }
                    get("/status") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                }
            }

            repeat(5) {
                client.get("/status").status shouldBe HttpStatusCode.OK
            }
        }
    }

    test("full application limiter behavior on /api routes") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api") {
                        installApiRateLimiting(requestsPerMinute = 2)
                        get("/display/status") { call.respond(HttpStatusCode.OK, mapOf("test" to true)) }
                    }
                }
            }

            client.get("/api/display/status").status shouldBe HttpStatusCode.OK
            client.get("/api/display/status").status shouldBe HttpStatusCode.OK
            client.get("/api/display/status").status shouldBe HttpStatusCode.TooManyRequests
        }
    }
})
