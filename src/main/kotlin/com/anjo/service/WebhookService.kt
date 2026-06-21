package com.anjo.service

import com.anjo.config.model.WebhooksConfig
import com.anjo.model.Schedule
import com.anjo.model.WebhookPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class WebhookService(
    private val httpClient: HttpClient,
    private val config: WebhooksConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val log = LoggerFactory.getLogger(WebhookService::class.java)

    fun send(schedule: Schedule, firedAt: Instant) {
        val url = resolveUrl(schedule) ?: return
        scope.launch { executePost(url, buildPayload(schedule, firedAt)) }
    }

    fun stop() {
        scope.coroutineContext[Job]?.cancel()
        httpClient.close()
    }

    fun willSend(schedule: Schedule): Boolean = resolveUrl(schedule) != null

    private suspend fun executePost(url: String, payload: WebhookPayload) {
        try {
            withTimeout(5_000.milliseconds) {
                val response = httpClient.post(url) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(payload)
                }
                if (!response.status.isSuccess()) {
                    log.warn("Webhook non-2xx: url=$url status=${response.status.value}")
                }
            }
        } catch (e: TimeoutCancellationException) {
            log.warn("Webhook timeout: url=$url")
        } catch (e: Exception) {
            log.warn("Webhook failed: url=$url error=${e.message}")
        }
    }

    private fun resolveUrl(schedule: Schedule): String? =
        schedule.webhookUrl?.takeIf { it.isNotBlank() }
            ?: config.defaultUrl?.takeIf { it.isNotBlank() }

    private fun buildPayload(schedule: Schedule, firedAt: Instant) = WebhookPayload(
        scheduleId = schedule.id,
        text = schedule.text,
        effect = schedule.effect.name,
        zoneId = schedule.zoneId,
        firedAt = firedAt.toString()
    )

    companion object {
        fun create(config: WebhooksConfig): WebhookService {
            val client = HttpClient(CIO) {
                install(ContentNegotiation) { json() }
            }
            return WebhookService(client, config)
        }
    }
}
