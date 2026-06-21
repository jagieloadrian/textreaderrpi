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
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebhookServiceTest : FunSpec({

    val firedAt = Instant.parse("2026-06-15T20:00:00Z")

    fun makeClient(
        captured: MutableList<io.ktor.client.request.HttpRequestData>,
        latch: CountDownLatch? = null,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient =
        HttpClient(MockEngine { request ->
            captured.add(request)
            latch?.countDown()
            respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json() }
        }

    test("HOOK-01 + HOOK-02: should POST JSON payload to schedule webhookUrl") {
        val capturedData = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val latch = CountDownLatch(1)
        val schedule = Schedule(
            id = "s1", text = "hello",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = "http://localhost:9999/hook"
        )
        WebhookService(makeClient(capturedData, latch), WebhooksConfig(defaultUrl = null)).send(schedule, firedAt)
        latch.await(2, TimeUnit.SECONDS)

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

    test("HOOK-02 zoneId: should include zoneId in payload when set and when null") {
        val capturedWithZone = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val latchWithZone = CountDownLatch(1)
        val scheduleWithZone = Schedule(
            id = "s2", text = "zoned",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = "http://localhost:9999/hook",
            zoneId = "zone-A"
        )
        WebhookService(makeClient(capturedWithZone, latchWithZone), WebhooksConfig(defaultUrl = null)).send(scheduleWithZone, firedAt)
        latchWithZone.await(2, TimeUnit.SECONDS)
        capturedWithZone[0].body.toByteArray().decodeToString() shouldContain "\"zoneId\":\"zone-A\""

        val capturedNoZone = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val latchNoZone = CountDownLatch(1)
        val scheduleNoZone = Schedule(
            id = "s2b", text = "no-zone",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = "http://localhost:9999/hook",
            zoneId = null
        )
        WebhookService(makeClient(capturedNoZone, latchNoZone), WebhooksConfig(defaultUrl = null)).send(scheduleNoZone, firedAt)
        latchNoZone.await(2, TimeUnit.SECONDS)
        capturedNoZone[0].body.toByteArray().decodeToString() shouldContain "\"zoneId\":null"
    }

    test("HOOK-03: should POST to config.defaultUrl when schedule.webhookUrl is null") {
        val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val latch = CountDownLatch(1)
        val schedule = Schedule(
            id = "s3", text = "fallback",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = null
        )
        WebhookService(makeClient(capturedRequests, latch), WebhooksConfig(defaultUrl = "http://fallback:8080/hook")).send(schedule, firedAt)
        latch.await(2, TimeUnit.SECONDS)

        capturedRequests.size shouldBe 1
        capturedRequests[0].url.toString() shouldBe "http://fallback:8080/hook"
    }

    test("D-04: should POST to schedule.webhookUrl when both schedule and config URL are set") {
        val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val latch = CountDownLatch(1)
        val schedule = Schedule(
            id = "s4", text = "priority",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = "http://explicit/hook"
        )
        WebhookService(makeClient(capturedRequests, latch), WebhooksConfig(defaultUrl = "http://fallback/hook")).send(schedule, firedAt)
        latch.await(2, TimeUnit.SECONDS)

        capturedRequests.size shouldBe 1
        capturedRequests[0].url.toString() shouldBe "http://explicit/hook"
    }

    test("D-05: should send zero requests when both webhookUrl and defaultUrl are null") {
        val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val schedule = Schedule(
            id = "s5", text = "silent",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = null
        )
        WebhookService(makeClient(capturedRequests), WebhooksConfig(defaultUrl = null)).send(schedule, firedAt)

        capturedRequests.size shouldBe 0
    }

    test("D-16: should not throw when webhook response is 500") {
        val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val latch = CountDownLatch(1)
        val schedule = Schedule(
            id = "s6", text = "error-response",
            triggerType = TriggerType.ONESHOT, triggerValue = "",
            effect = Effect.SCROLL,
            webhookUrl = "http://localhost:9999/hook"
        )
        WebhookService(makeClient(capturedRequests, latch, HttpStatusCode.InternalServerError), WebhooksConfig(defaultUrl = null)).send(schedule, firedAt)
        latch.await(2, TimeUnit.SECONDS)

        capturedRequests.size shouldBe 1
    }
})
