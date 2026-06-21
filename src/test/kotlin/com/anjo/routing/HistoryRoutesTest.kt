package com.anjo.routing

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryRecord
import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication

class HistoryRoutesTest : FunSpec({

    test("GET /api/v1/history returns 200 with items page size total envelope") {
        testApplication {
            application { module() }
            client.get("/health")
            val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            repeat(25) { i ->
                historyRepository.insert(
                    HistoryRecord(text = "text $i", effect = if (i % 2 == 0) "SCROLL" else "BLINK", source = if (i % 3 == 0) "SCHEDULED" else "IMMEDIATE", scheduleId = if (i % 3 == 0) "sched-$i" else null)
                )
            }
            val response = client.get("/api/v1/history")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"items\""
            body shouldContain "\"page\""
            body shouldContain "\"size\""
            body shouldContain "\"total\""
        }
    }

    test("GET /api/v1/history page 2 returns different slice than page 1") {
        testApplication {
            application { module() }
            client.get("/health")
            val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            repeat(25) { i ->
                historyRepository.insert(HistoryRecord(text = "pagination-text-$i", effect = "SCROLL", source = "IMMEDIATE"))
            }
            val page1Body = client.get("/api/v1/history?page=1&size=10").bodyAsText()
            val page2Body = client.get("/api/v1/history?page=2&size=10").bodyAsText()
            (page1Body == page2Body) shouldBe false
        }
    }

    test("GET /api/v1/history with effect=SCROLL returns only SCROLL records") {
        testApplication {
            application { module() }
            client.get("/health")
            val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            repeat(5) { historyRepository.insert(HistoryRecord(text = "scroll-text", effect = "SCROLL", source = "IMMEDIATE")) }
            repeat(5) { historyRepository.insert(HistoryRecord(text = "blink-text", effect = "BLINK", source = "IMMEDIATE")) }
            val body = client.get("/api/v1/history?effect=SCROLL").bodyAsText()
            body shouldContain "SCROLL"
            body shouldNotContain "BLINK"
        }
    }

    test("GET /api/v1/history with source=IMMEDIATE returns only IMMEDIATE records") {
        testApplication {
            application { module() }
            client.get("/health")
            val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            repeat(5) { historyRepository.insert(HistoryRecord(text = "imm-text", effect = "SCROLL", source = "IMMEDIATE")) }
            repeat(5) { historyRepository.insert(HistoryRecord(text = "sched-text", effect = "SCROLL", source = "SCHEDULED", scheduleId = "sched-id")) }
            val body = client.get("/api/v1/history?source=IMMEDIATE").bodyAsText()
            body shouldContain "IMMEDIATE"
            body shouldNotContain "SCHEDULED"
        }
    }
})
