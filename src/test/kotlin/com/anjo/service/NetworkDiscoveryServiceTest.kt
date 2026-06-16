package com.anjo.service

import com.anjo.db.ZoneRepository
import com.anjo.model.NetworkZone
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.headersOf
import io.ktor.http.HttpStatusCode
import java.time.Instant

class NetworkDiscoveryServiceTest : FunSpec({

    fun makeWsClient(): HttpClient =
        HttpClient(MockEngine { respond("", HttpStatusCode.SwitchingProtocols, headersOf()) }) {
            install(WebSockets)
        }

    test("onDeviceDiscovered calls zoneRepository.upsert with correct fields") {
        val zoneRepository = mockk<ZoneRepository>(relaxed = true)
        val zoneRegistry = mockk<ZoneRegistry>(relaxed = true)
        val wsClient = makeWsClient()
        coEvery { zoneRepository.upsert(any()) } returns Unit

        val service = NetworkDiscoveryService(
            zoneRegistry = zoneRegistry,
            zoneRepository = zoneRepository,
            wsClient = wsClient
        )

        service.testOnDeviceDiscovered(ip = "192.168.1.50", method = "UDP", name = "kitchen")

        val slot = slot<NetworkZone>()
        coVerify { zoneRepository.upsert(capture(slot)) }
        slot.captured.ip shouldBe "192.168.1.50"
        slot.captured.discoveryMethod shouldBe "UDP"
    }

    test("onDeviceDiscovered calls zoneRegistry.addNetworkZone after upsert") {
        val zoneRepository = mockk<ZoneRepository>(relaxed = true)
        val zoneRegistry = mockk<ZoneRegistry>(relaxed = true)
        val wsClient = makeWsClient()
        coEvery { zoneRepository.upsert(any()) } returns Unit

        val service = NetworkDiscoveryService(
            zoneRegistry = zoneRegistry,
            zoneRepository = zoneRepository,
            wsClient = wsClient
        )

        service.testOnDeviceDiscovered(ip = "192.168.1.51", method = "MDNS", name = "lounge")

        coVerify { zoneRepository.upsert(any()) }
        coVerify { zoneRegistry.addNetworkZone(any(), any()) }
    }

    test("start and stop do not throw") {
        val zoneRepository = mockk<ZoneRepository>(relaxed = true)
        val zoneRegistry = mockk<ZoneRegistry>(relaxed = true)
        val wsClient = makeWsClient()

        val service = NetworkDiscoveryService(
            zoneRegistry = zoneRegistry,
            zoneRepository = zoneRepository,
            wsClient = wsClient
        )

        service.start()
        service.stop()
    }
})
