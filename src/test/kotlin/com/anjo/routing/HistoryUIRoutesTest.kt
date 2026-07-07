package com.anjo.routing

import com.anjo.appTest
import com.anjo.db.HistoryRepository
import com.anjo.dep
import com.anjo.model.HistoryRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

class HistoryUIRoutesTest : FunSpec({

    test("GET /history returns 200 with Display History heading") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            historyRepository.insert(HistoryRecord(text = "hello world", effect = "SCROLL", source = "IMMEDIATE"))
            val response = client.get("/history")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Display History"
        }
    }

    test("GET /history contains effect and source filter dropdowns") {
        appTest {
            val response = client.get("/history")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "name=\"effect\""
            body shouldContain "name=\"source\""
        }
    }

    test("GET /history?expand=all renders details with open attribute") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            historyRepository.insert(HistoryRecord(text = "expand test", effect = "BLINK", source = "IMMEDIATE"))
            val body = client.get("/history?expand=all").bodyAsText()
            body shouldContain "<details open"
        }
    }

    test("GET /history nav contains href for /history") {
        appTest {
            val body = client.get("/history").bodyAsText()
            body shouldContain "href=\"/history\""
        }
    }

    test("GET /history page contains search input field") {
        appTest {
            val body = client.get("/history").bodyAsText()
            body shouldContain "name=\"search\""
        }
    }

    test("GET /history page contains Export CSV link pointing to export endpoint") {
        appTest {
            val body = client.get("/history").bodyAsText()
            body shouldContain "Export CSV"
            body shouldContain "/api/v1/history/export"
        }
    }

    test("GET /history with search param highlights matching term with mark element") {
        appTest {
            val historyRepository = dep<HistoryRepository>()
            historyRepository.insert(HistoryRecord(text = "hello world", effect = "SCROLL", source = "IMMEDIATE"))
            val body = client.get("/history?search=hello").bodyAsText()
            body shouldContain "<mark>hello</mark>"
        }
    }
})
