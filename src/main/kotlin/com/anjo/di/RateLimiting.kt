package com.anjo.di

import io.github.flaxoos.ktor.server.plugins.ratelimiter.RateLimiting
import io.github.flaxoos.ktor.server.plugins.ratelimiter.implementations.TokenBucket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.time.Duration.Companion.minutes

private const val RETRY_AFTER_SECONDS = "60"

fun Route.installApiRateLimiting(requestsPerMinute: Int) {
    install(RateLimiting) {
        rateLimiter {
            type = TokenBucket::class
            rate = 1.minutes
            capacity = requestsPerMinute.coerceAtLeast(1)
        }

        rateLimitExceededHandler = {
            this.response.header(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS)
            this.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("error" to "Rate limit exceeded. Retry-After: ${RETRY_AFTER_SECONDS}s")
            )
        }
    }
}
