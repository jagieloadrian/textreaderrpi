package com.anjo.routing

import com.anjo.appTest
import com.anjo.db.HistoryRepository
import com.anjo.dep
import com.anjo.historyRecord
import com.anjo.model.HistoryRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.kotest.matchers.string.shouldStartWith

class HistoryRoutesTest : FunSpec({

    test("GET /api/v1/history returns 200 with items page size total envelope") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            repeat(25) { i ->
                historyRepository.insert(
                    historyRecord(i, effect = if (i % 2 == 0) "SCROLL" else "BLINK", source = if (i % 3 == 0) "SCHEDULED" else "IMMEDIATE", scheduleId = if (i % 3 == 0) "sched-$i" else null)
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
        appTest {
            val historyRepository = dep<HistoryRepository>()
            repeat(25) { i ->
                historyRepository.insert(historyRecord(i, source = "IMMEDIATE"))
            }
            val page1Body = client.get("/api/v1/history?page=1&size=10").bodyAsText()
            val page2Body = client.get("/api/v1/history?page=2&size=10").bodyAsText()
            (page1Body == page2Body) shouldBe false
        }
    }

    test("GET /api/v1/history with effect=SCROLL returns only SCROLL records") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            repeat(5) { historyRepository.insert(HistoryRecord(text = "scroll-text", effect = "SCROLL", source = "IMMEDIATE")) }
            repeat(5) { historyRepository.insert(HistoryRecord(text = "blink-text", effect = "BLINK", source = "IMMEDIATE")) }
            val body = client.get("/api/v1/history?effect=SCROLL").bodyAsText()
            body shouldContain "SCROLL"
            body shouldNotContain "BLINK"
        }
    }

    test("GET /api/v1/history with source=IMMEDIATE returns only IMMEDIATE records") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            repeat(5) { historyRepository.insert(HistoryRecord(text = "imm-text", effect = "SCROLL", source = "IMMEDIATE")) }
            repeat(5) { historyRepository.insert(HistoryRecord(text = "sched-text", effect = "SCROLL", source = "SCHEDULED", scheduleId = "sched-id")) }
            val body = client.get("/api/v1/history?source=IMMEDIATE").bodyAsText()
            body shouldContain "IMMEDIATE"
            body shouldNotContain "SCHEDULED"
        }
    }

    test("GET /api/v1/history?search=hello returns only records whose text contains hello") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            historyRepository.insert(HistoryRecord(text = "hello world", effect = "SCROLL", source = "IMMEDIATE"))
            historyRepository.insert(HistoryRecord(text = "world only", effect = "SCROLL", source = "IMMEDIATE"))
            val body = client.get("/api/v1/history?search=hello").bodyAsText()
            body shouldContain "hello world"
            body shouldNotContain "world only"
        }
    }

    test("GET /api/v1/history search is case-insensitive") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            historyRepository.insert(HistoryRecord(text = "hello world", effect = "SCROLL", source = "IMMEDIATE"))
            historyRepository.insert(HistoryRecord(text = "world only", effect = "SCROLL", source = "IMMEDIATE"))
            val body = client.get("/api/v1/history?search=HELLO").bodyAsText()
            body shouldContain "hello world"
            body shouldNotContain "world only"
        }
    }

    test("GET /api/v1/history/export returns 200 with Content-Disposition attachment header") {
        appTest {
            val response = client.get("/api/v1/history/export")
            response.status shouldBe HttpStatusCode.OK
            val disposition = response.headers["Content-Disposition"] ?: ""
            disposition shouldContain "attachment"
            disposition shouldContain "history.csv"
        }
    }

    test("GET /api/v1/history/export body first line equals the CSV header row") {
        appTest {
            val body = client.get("/api/v1/history/export").bodyAsText()
            body shouldStartWith "ID,Text,Effect,Source,Zone ID,Schedule ID,Displayed At,Webhook Status"
        }
    }

    test("GET /api/v1/history/export with effect=SCROLL excludes BLINK rows from CSV body") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            historyRepository.insert(HistoryRecord(text = "scroll-row", effect = "SCROLL", source = "IMMEDIATE"))
            historyRepository.insert(HistoryRecord(text = "blink-row", effect = "BLINK", source = "IMMEDIATE"))
            val body = client.get("/api/v1/history/export?effect=SCROLL").bodyAsText()
            body shouldContain "scroll-row"
            body shouldNotContain "blink-row"
        }
    }
})
