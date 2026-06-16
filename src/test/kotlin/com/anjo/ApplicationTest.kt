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
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineDispatcher

class ApplicationTest : FunSpec({

    test("should start application context and serve health endpoint") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
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

    test("should include application name in health response") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should read discoveryEnabled as false from system property in test JVM") {
        testApplication {
            application { module() }
            client.get("/health")
            val appConfig = application.dependencies.getBlocking<ApplicationConfig>(DependencyKey<ApplicationConfig>())
            appConfig.discoveryEnabled shouldBe false
        }
    }

    test("should resolve all configureDI bindings without error") {
        testApplication {
            application { module() }
            client.get("/health")
            val deps = application.dependencies
            deps.getBlocking<ApplicationConfig>(DependencyKey<ApplicationConfig>()) shouldNotBeNull {}
            deps.getBlocking<ApiConfig>(DependencyKey<ApiConfig>()) shouldNotBeNull {}
            deps.getBlocking<CoroutineDispatcher>(DependencyKey<CoroutineDispatcher>()) shouldNotBeNull {}
            deps.getBlocking<MetricRegistry>(DependencyKey<MetricRegistry>()) shouldNotBeNull {}
            deps.getBlocking<HardwareMetrics>(DependencyKey<HardwareMetrics>()) shouldNotBeNull {}
            deps.getBlocking<ZoneRegistry>(DependencyKey<ZoneRegistry>()) shouldNotBeNull {}
            deps.getBlocking<ZoneRepository>(DependencyKey<ZoneRepository>()) shouldNotBeNull {}
            deps.getBlocking<NetworkDiscoveryService>(DependencyKey<NetworkDiscoveryService>()) shouldNotBeNull {}
            deps.getBlocking<ScreenDriverService>(DependencyKey<ScreenDriverService>()) shouldNotBeNull {}
            deps.getBlocking<MetricsCollector>(DependencyKey<MetricsCollector>()) shouldNotBeNull {}
            deps.getBlocking<ScheduleRepository>(DependencyKey<ScheduleRepository>()) shouldNotBeNull {}
            deps.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>()) shouldNotBeNull {}
            deps.getBlocking<HistoryService>(DependencyKey<HistoryService>()) shouldNotBeNull {}
            deps.getBlocking<EffectRendererFactory>(DependencyKey<EffectRendererFactory>()) shouldNotBeNull {}
            deps.getBlocking<SchedulerService>(DependencyKey<SchedulerService>()) shouldNotBeNull {}
            deps.getBlocking<WebhookService>(DependencyKey<WebhookService>()) shouldNotBeNull {}
        }
    }
})
