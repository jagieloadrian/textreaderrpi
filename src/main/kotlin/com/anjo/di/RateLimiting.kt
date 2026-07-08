package com.anjo.di

import io.github.flaxoos.ktor.server.plugins.ratelimiter.RateLimiting
import io.github.flaxoos.ktor.server.plugins.ratelimiter.implementations.TokenBucket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlin.time.Duration.Companion.minutes

fun Route.installRateLimiting(requestsPerMinute: Int, retryAfterSeconds: String, exceededMessage: String) {
    install(RateLimiting) {
        rateLimiter {
            type = TokenBucket::class
            rate = 1.minutes
            capacity = requestsPerMinute.coerceAtLeast(1)
        }

        rateLimitExceededHandler = {
            this.response.header(HttpHeaders.RetryAfter, retryAfterSeconds)
            this.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("error" to "$exceededMessage. Retry-After: ${retryAfterSeconds}s")
            )
        }
    }
}

fun Route.installApiRateLimiting(requestsPerMinute: Int) =
    installRateLimiting(requestsPerMinute, "60", "Rate limit exceeded")

fun Route.installMetricsRateLimiting(requestsPerMinute: Int) =
    installRateLimiting(requestsPerMinute, "30", "Metrics rate limit exceeded")
