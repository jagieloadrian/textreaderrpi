package com.anjo.di
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
/**
 * Fixed-window in-memory rate limiter.
 * Keyed by client IP address; window resets each minute.
 */
class RateLimiter(val requestsPerMinute: Int = 60) {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()
    private val windowStarts = ConcurrentHashMap<String, AtomicLong>()
    private val windowMs = 60_000L
    data class Decision(val allowed: Boolean, val retryAfterSeconds: Long)
    fun check(clientKey: String): Decision {
        val now = System.currentTimeMillis()
        val start = windowStarts.getOrPut(clientKey) { AtomicLong(now) }
        val counter = counters.getOrPut(clientKey) { AtomicInteger(0) }
        if (now - start.get() >= windowMs) {
            start.set(now)
            counter.set(0)
        }
        return if (counter.incrementAndGet() <= requestsPerMinute) {
            Decision(allowed = true, retryAfterSeconds = 0L)
        } else {
            val retryAfter = ((windowMs - (now - start.get())) / 1000).coerceAtLeast(1L)
            Decision(allowed = false, retryAfterSeconds = retryAfter)
        }
    }
}
class RateLimiterConfig {
    var limiter: RateLimiter = RateLimiter()
    var pathPrefixes: Set<String> = setOf("/api", "/health")
}
val RateLimitPlugin = createRouteScopedPlugin("RateLimit", ::RateLimiterConfig) {
    val limiter = pluginConfig.limiter
    val prefixes = pluginConfig.pathPrefixes
    onCall { call ->
        val path = call.request.path()
        val shouldLimit = prefixes.isEmpty() || prefixes.any { path.startsWith(it) }
        if (shouldLimit) {
            // Use forwarded IP header first, fall back to local remote address
            val clientKey = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                ?: call.request.local.remoteAddress
            val decision = limiter.check(clientKey)
            if (!decision.allowed) {
                call.response.header(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf("error" to "Rate limit exceeded. Retry-After: ${decision.retryAfterSeconds}s")
                )
            }
        }
    }
}
