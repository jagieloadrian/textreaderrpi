package com.anjo.routing

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryRecord
import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking

class HistoryUIRoutesTest : FunSpec({

    test("GET /history returns 200 with Display History heading") {
        testApplication {
            application { module() }
            client.get("/health")
            val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            runBlocking {
                historyRepository.insert(HistoryRecord(text = "hello world", effect = "SCROLL", source = "IMMEDIATE"))
            }
            val response = client.get("/history")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Display History"
        }
    }

    test("GET /history contains effect and source filter dropdowns") {
        testApplication {
            application { module() }
            client.get("/health")
            val response = client.get("/history")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "name=\"effect\""
            body shouldContain "name=\"source\""
        }
    }

    test("GET /history?expand=all renders details with open attribute") {
        testApplication {
            application { module() }
            client.get("/health")
            val historyRepository = application.dependencies.getBlocking<HistoryRepository>(DependencyKey<HistoryRepository>())
            runBlocking {
                historyRepository.insert(HistoryRecord(text = "expand test", effect = "BLINK", source = "IMMEDIATE"))
            }
            val body = client.get("/history?expand=all").bodyAsText()
            body shouldContain "<details open"
        }
    }

    test("GET /history nav contains href for /history") {
        testApplication {
            application { module() }
            client.get("/health")
            val body = client.get("/history").bodyAsText()
            body shouldContain "href=\"/history\""
        }
    }
})
