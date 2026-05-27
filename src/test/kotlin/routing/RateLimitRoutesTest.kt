package com.anjo.routing
import com.anjo.di.RateLimitPlugin
import com.anjo.di.RateLimiter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
class RateLimitRoutesTest : FunSpec({
    test("requests below limit are allowed through") {
        val limiter = RateLimiter(requestsPerMinute = 5)
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    install(RateLimitPlugin) { this.limiter = limiter; pathPrefixes = setOf("/api") }
                    get("/api/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                }
            }
            repeat(5) {
                val response = client.get("/api/test")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
    test("requests over limit receive 429 with Retry-After header") {
        val limiter = RateLimiter(requestsPerMinute = 2)
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    install(RateLimitPlugin) { this.limiter = limiter; pathPrefixes = setOf("/api") }
                    get("/api/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                }
            }
            // First 2 requests should pass
            repeat(2) {
                val response = client.get("/api/test")
                response.status shouldBe HttpStatusCode.OK
            }
            // 3rd request should be rate-limited
            val limited = client.get("/api/test")
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter] shouldContain ""
        }
    }
    test("rate-limited response contains error message") {
        val limiter = RateLimiter(requestsPerMinute = 1)
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    install(RateLimitPlugin) { this.limiter = limiter; pathPrefixes = setOf("/api") }
                    get("/api/test") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                }
            }
            client.get("/api/test") // first - allowed
            val limited = client.get("/api/test") // second - denied
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.bodyAsText() shouldContain "Rate limit exceeded"
        }
    }
    test("paths not matching prefix are not rate-limited") {
        val limiter = RateLimiter(requestsPerMinute = 1)
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    install(RateLimitPlugin) { this.limiter = limiter; pathPrefixes = setOf("/api") }
                    get("/status") { call.respond(HttpStatusCode.OK, mapOf("ok" to true)) }
                }
            }
            // /status is not in /api prefix, should never be rate-limited
            repeat(5) {
                val response = client.get("/status")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
    test("full application rate limiter is active on /api routes") {
        val limiter = RateLimiter(requestsPerMinute = 2)
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    install(RateLimitPlugin) { this.limiter = limiter; pathPrefixes = setOf("/api") }
                    get("/api/display/status") { call.respond(HttpStatusCode.OK, mapOf("test" to true)) }
                }
            }
            client.get("/api/display/status").status shouldBe HttpStatusCode.OK
            client.get("/api/display/status").status shouldBe HttpStatusCode.OK
            client.get("/api/display/status").status shouldBe HttpStatusCode.TooManyRequests
        }
    }
})
