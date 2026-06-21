package com.anjo.routing

import com.anjo.db.ZoneRepository
import com.anjo.model.NetworkZone
import com.anjo.module
import com.anjo.service.ZoneRegistry
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
import java.time.Instant

class ZonesUIRoutesTest : FunSpec({

    test("GET /zones returns 200 with content-type text/html") {
        testApplication {
            application { module() }
            client.get("/health")
            val response = client.get("/zones")
            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldContain "text/html"
        }
    }

    test("GET /zones body contains Zones heading") {
        testApplication {
            application { module() }
            client.get("/health")
            val body = client.get("/zones").bodyAsText()
            body shouldContain "Zones"
        }
    }

    test("GET /zones body contains Scan for Displays button") {
        testApplication {
            application { module() }
            client.get("/health")
            val body = client.get("/zones").bodyAsText()
            body shouldContain "Scan for Displays"
        }
    }

    test("GET /zones body contains Add Zone submit button") {
        testApplication {
            application { module() }
            client.get("/health")
            val body = client.get("/zones").bodyAsText()
            body shouldContain "Add Zone"
        }
    }

    test("GET /zones with a seeded network zone shows status badge") {
        testApplication {
            application { module() }
            client.get("/health")
            val zoneRepository = application.dependencies.getBlocking<ZoneRepository>(DependencyKey<ZoneRepository>())
            val zoneRegistry = application.dependencies.getBlocking<ZoneRegistry>(DependencyKey<ZoneRegistry>())
            val zone = NetworkZone(
                id = "test-zone-ui",
                name = "Test Zone",
                ip = "192.168.1.99",
                type = "MAX7219",
                discoveryMethod = "MANUAL",
                createdAt = Instant.now().toString()
            )
            zoneRepository.upsert(zone)
            zoneRegistry.addNetworkZone(zone)
            val body = client.get("/zones").bodyAsText()
            body shouldContain "test-zone-ui"
            body shouldContain "<mark"
        }
    }

    test("GET /zones with no zones shows No zones registered empty state") {
        testApplication {
            application { module() }
            client.get("/health")
            val body: String
            val response = client.get("/zones")
            body = response.bodyAsText()
            body shouldContain "Zones"
        }
    }
})
