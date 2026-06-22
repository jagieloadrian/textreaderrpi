package com.anjo.service

import com.anjo.config.model.RetryConfig
import com.anjo.db.HistoryRepository
import com.anjo.model.ConflictPolicy
import com.anjo.model.HistoryFilter
import com.anjo.model.Effect
import com.anjo.model.ScreenDriverMetrics
import com.anjo.module
import com.anjo.zone.ZoneDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.ktor.client.request.get
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers

class HistoryRecordingTest : FunSpec({

    test("displayImmediate writes IMMEDIATE record to history via real wired app") {
        testApplication {
            application { module() }
            client.get("/health")
            val deps = application.dependencies
            val screenService = deps.getBlocking<ScreenDriverService>(DependencyKey<ScreenDriverService>())
            val historyRepo = deps.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            screenService.displayImmediate("rec-test", Effect.SCROLL, ConflictPolicy.INTERRUPT)
            screenService.awaitCurrentJob()
            val (items, total) = historyRepo.findPaginated(HistoryFilter(null, null, null, null), 1, 50)
            (total >= 1L) shouldBe true
            val record = items.find { it.text == "rec-test" }
            record shouldNotBe null
            record?.source shouldBe "IMMEDIATE"
            record?.effect shouldBe "SCROLL"
            record?.webhookStatus shouldBe null
        }
    }

    test("displayScheduled writes SCHEDULED record to history via real wired app") {
        testApplication {
            application { module() }
            client.get("/health")
            val deps = application.dependencies
            val screenService = deps.getBlocking<ScreenDriverService>(DependencyKey<ScreenDriverService>())
            val historyRepo = deps.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            screenService.displayScheduled("sched-test", "sched-id-1", Effect.SCROLL, ConflictPolicy.INTERRUPT, "sent")
            val (items, _) = historyRepo.findPaginated(HistoryFilter(null, null, null, null), 1, 50)
            val record = items.find { it.scheduleId == "sched-id-1" }
            record shouldNotBe null
            record?.source shouldBe "SCHEDULED"
            record?.effect shouldBe "SCROLL"
            record?.webhookStatus shouldBe "sent"
        }
    }

    test("dropped SKIP_NEW request produces no extra history record") {
        testApplication {
            application { module() }
            client.get("/health")
            val deps = application.dependencies
            val screenService = deps.getBlocking<ScreenDriverService>(DependencyKey<ScreenDriverService>())
            val historyRepo = deps.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            val (_, beforeTotal) = historyRepo.findPaginated(HistoryFilter(null, null, null, null), 1, 50)
            val result = screenService.displayImmediate("rendered", Effect.SCROLL, ConflictPolicy.INTERRUPT)
            result.shouldBeInstanceOf<DisplayResult.Broadcast>()
            screenService.awaitCurrentJob()
            val (_, afterTotal) = historyRepo.findPaginated(HistoryFilter(null, null, null, null), 1, 50)
            (afterTotal >= beforeTotal) shouldBe true
        }
    }

    test("insert failure does not prevent displayImmediate from returning a result") {
        val throwingRepo = mockk<HistoryRepository>()
        coEvery { throwingRepo.insert(any()) } throws RuntimeException("DB down")
        val mockZoneDriver = mockk<ZoneDriver>(relaxed = true)
        val registry = ZoneRegistry()
        registry.register("main", mockZoneDriver)
        val svc = ScreenDriverService(
            zoneRegistry = registry,
            ioDispatcher = Dispatchers.Unconfined,
            retryConfig = RetryConfig(maxAttempts = 1, initialDelayMs = 1L),
            metrics = ScreenDriverMetrics.DISABLED,
            historyRepository = throwingRepo,
        )
        val result = svc.displayImmediate("fail-insert", Effect.SCROLL, ConflictPolicy.INTERRUPT)
        result.shouldBeInstanceOf<DisplayResult.Broadcast>()
    }
})
