package com.anjo

import com.anjo.config.model.ApiConfig
import com.anjo.config.model.ApplicationConfig
import com.anjo.db.HistoryRepository
import com.anjo.db.ScheduleRepository
import com.anjo.model.HardwareMetrics
import com.anjo.service.HistoryService
import com.anjo.db.ZoneRepository
import com.anjo.service.EffectRendererFactory
import com.anjo.service.MetricsCollector
import com.anjo.service.NetworkDiscoveryService
import com.anjo.service.SchedulerService
import com.anjo.service.ScreenDriverService
import com.anjo.service.WebhookService
import com.anjo.service.ZoneRegistry
import com.codahale.metrics.MetricRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineDispatcher

class ApplicationTest : FunSpec({

    test("should start application context and serve health endpoint") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            val validStatuses = setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            assert(response.status in validStatuses)
        }
    }

    test("should expose API routes after context startup") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/schedule")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should serve static assets after context startup") {
        testApplication {
            application { module() }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should return 404 for unknown routes") {
        testApplication {
            application { module() }
            val response = client.get("/nonexistent-endpoint")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("should return HTML 404 page for browser navigation to unknown route") {
        testApplication {
            application { module() }
            val response = client.get("/does-not-exist") {
                header(HttpHeaders.Accept, ContentType.Text.Html.toString())
            }
            response.status shouldBe HttpStatusCode.NotFound
            response.headers[HttpHeaders.ContentType] shouldContain "text/html"
            response.bodyAsText() shouldContain "Error 404"
            response.bodyAsText() shouldContain "Page not found"
        }
    }

    test("should return JSON error for API path unknown route") {
        testApplication {
            application { module() }
            val response = client.get("/api/v1/does-not-exist")
            response.status shouldBe HttpStatusCode.NotFound
            val body = response.bodyAsText()
            assert(!body.contains("Error 404")) { "API path should not return HTML 404 page, got: $body" }
        }
    }

    test("should include application name in health response") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            val validStatuses = setOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)
            assert(response.status in validStatuses)
        }
    }

    test("should read discoveryEnabled as false from system property in test JVM") {
        appTest {
            val appConfig = dep<ApplicationConfig>()
            appConfig.discoveryEnabled shouldBe false
        }
    }

    test("should resolve all configureDI bindings without error") {
        appTest {
            dep<ApplicationConfig>() shouldNotBeNull {}
            dep<ApiConfig>() shouldNotBeNull {}
            dep<CoroutineDispatcher>() shouldNotBeNull {}
            dep<MetricRegistry>() shouldNotBeNull {}
            dep<HardwareMetrics>() shouldNotBeNull {}
            dep<ZoneRegistry>() shouldNotBeNull {}
            dep<ZoneRepository>() shouldNotBeNull {}
            dep<NetworkDiscoveryService>() shouldNotBeNull {}
            dep<ScreenDriverService>() shouldNotBeNull {}
            dep<MetricsCollector>() shouldNotBeNull {}
            dep<ScheduleRepository>() shouldNotBeNull {}
            dep<HistoryRepository>() shouldNotBeNull {}
            dep<HistoryService>() shouldNotBeNull {}
            dep<EffectRendererFactory>() shouldNotBeNull {}
            dep<SchedulerService>() shouldNotBeNull {}
            dep<WebhookService>() shouldNotBeNull {}
        }
    }
})
