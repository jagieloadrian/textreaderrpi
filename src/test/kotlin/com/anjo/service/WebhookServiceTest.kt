package com.anjo.service

import com.anjo.config.model.WebhooksConfig
import com.anjo.model.Effect
import com.anjo.model.Schedule
import com.anjo.model.TriggerType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WebhookServiceTest : FunSpec({

    val firedAt = Instant.parse("2026-06-15T20:00:00Z")

    fun makeClient(captured: MutableList<io.ktor.client.request.HttpRequestData>, status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        HttpClient(MockEngine { request ->
            captured.add(request)
            respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json() }
        }

    test("HOOK-01 + HOOK-02: should POST JSON payload to schedule webhookUrl") {
        runTest {
            val capturedData = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val client = makeClient(capturedData)
            val schedule = Schedule(
                id = "s1", text = "hello",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = "http://localhost:9999/hook"
            )
            val service = WebhookService(client, WebhooksConfig(defaultUrl = null), this)
            service.send(schedule, firedAt)
            advanceUntilIdle()

            capturedData.size shouldBe 1
            capturedData[0].url.toString() shouldBe "http://localhost:9999/hook"
            capturedData[0].method shouldBe HttpMethod.Post
            capturedData[0].body.contentType.toString() shouldContain "application/json"
            val body = capturedData[0].body.toByteArray().decodeToString()
            body shouldContain "\"scheduleId\":\"s1\""
            body shouldContain "\"text\":\"hello\""
            body shouldContain "\"effect\":\"SCROLL\""
            body shouldContain "\"firedAt\""
        }
    }

    test("HOOK-02 zoneId: should include zoneId in payload when set and when null") {
        runTest {
            val capturedWithZone = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val clientWithZone = makeClient(capturedWithZone)
            val scheduleWithZone = Schedule(
                id = "s2", text = "zoned",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = "http://localhost:9999/hook",
                zoneId = "zone-A"
            )
            val serviceWithZone = WebhookService(clientWithZone, WebhooksConfig(defaultUrl = null), this)
            serviceWithZone.send(scheduleWithZone, firedAt)
            advanceUntilIdle()

            val bodyWithZone = capturedWithZone[0].body.toByteArray().decodeToString()
            bodyWithZone shouldContain "\"zoneId\":\"zone-A\""

            val capturedNoZone = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val clientNoZone = makeClient(capturedNoZone)
            val scheduleNoZone = Schedule(
                id = "s2b", text = "no-zone",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = "http://localhost:9999/hook",
                zoneId = null
            )
            val serviceNoZone = WebhookService(clientNoZone, WebhooksConfig(defaultUrl = null), this)
            serviceNoZone.send(scheduleNoZone, firedAt)
            advanceUntilIdle()

            val bodyNoZone = capturedNoZone[0].body.toByteArray().decodeToString()
            bodyNoZone shouldContain "\"zoneId\":null"
        }
    }

    test("HOOK-03: should POST to config.defaultUrl when schedule.webhookUrl is null") {
        runTest {
            val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val client = makeClient(capturedRequests)
            val schedule = Schedule(
                id = "s3", text = "fallback",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = null
            )
            val service = WebhookService(client, WebhooksConfig(defaultUrl = "http://fallback:8080/hook"), this)
            service.send(schedule, firedAt)
            advanceUntilIdle()

            capturedRequests.size shouldBe 1
            capturedRequests[0].url.toString() shouldBe "http://fallback:8080/hook"
        }
    }

    test("D-04: should POST to schedule.webhookUrl when both schedule and config URL are set") {
        runTest {
            val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val client = makeClient(capturedRequests)
            val schedule = Schedule(
                id = "s4", text = "priority",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = "http://explicit/hook"
            )
            val service = WebhookService(client, WebhooksConfig(defaultUrl = "http://fallback/hook"), this)
            service.send(schedule, firedAt)
            advanceUntilIdle()

            capturedRequests.size shouldBe 1
            capturedRequests[0].url.toString() shouldBe "http://explicit/hook"
        }
    }

    test("D-05: should send zero requests when both webhookUrl and defaultUrl are null") {
        runTest {
            val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val client = makeClient(capturedRequests)
            val schedule = Schedule(
                id = "s5", text = "silent",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = null
            )
            val service = WebhookService(client, WebhooksConfig(defaultUrl = null), this)
            service.send(schedule, firedAt)
            advanceUntilIdle()

            capturedRequests.size shouldBe 0
        }
    }

    test("D-16: should not throw when webhook response is 500") {
        runTest {
            val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val client = makeClient(capturedRequests, HttpStatusCode.InternalServerError)
            val schedule = Schedule(
                id = "s6", text = "error-response",
                triggerType = TriggerType.ONESHOT, triggerValue = "",
                effect = Effect.SCROLL,
                webhookUrl = "http://localhost:9999/hook"
            )
            val service = WebhookService(client, WebhooksConfig(defaultUrl = null), this)
            service.send(schedule, firedAt)
            advanceUntilIdle()

            capturedRequests.size shouldBe 1
        }
    }
})
