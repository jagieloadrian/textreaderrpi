package com.anjo.routing

import com.anjo.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MetricsRoutesTest : FunSpec({
    test("should return 200 JSON for GET /metrics") {
        testApplication {
            application { module() }
            val response = client.get("/metrics")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("should include timestamp and 2 groups in metrics response") {
        testApplication {
            application { module() }
            val json = Json.parseToJsonElement(client.get("/metrics").bodyAsText()).jsonObject
            json["timestamp"]!!.jsonPrimitive.content.shouldNotBeEmpty()
            json["groups"]!!.jsonArray shouldHaveSize 2
        }
    }

    test("should have runtime and api groups in metrics") {
        testApplication {
            application { module() }
            val json = Json.parseToJsonElement(client.get("/metrics").bodyAsText()).jsonObject
            val groupNames = json["groups"]!!.jsonArray.map {
                it.jsonObject["name"]!!.jsonPrimitive.content
            }
            groupNames shouldBe listOf("runtime", "api")
        }
    }

    test("should contain uptime and jvm memory metrics in runtime group") {
        testApplication {
            application { module() }
            val json = Json.parseToJsonElement(client.get("/metrics").bodyAsText()).jsonObject
            val metricKeys = json["groups"]!!.jsonArray[0].jsonObject["metrics"]!!.jsonArray.map {
                it.jsonObject["key"]!!.jsonPrimitive.content
            }
            metricKeys.contains("uptime") shouldBe true
            metricKeys.contains("jvm.memory.used") shouldBe true
            metricKeys.contains("jvm.memory.max") shouldBe true
        }
    }
})
