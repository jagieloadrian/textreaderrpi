package com.anjo.service

import com.anjo.model.Effect
import com.anjo.model.ZoneStatus
import com.anjo.zone.ZoneDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class ZoneRegistryTest : FunSpec({

    fun makeRegistry(vararg pairs: Pair<String, ZoneDriver>): ZoneRegistry {
        val registry = ZoneRegistry()
        pairs.forEach { (id, driver) -> registry.register(id, driver) }
        return registry
    }

    test("route to a known zone calls that driver and returns its result") {
        val driver = mockk<ZoneDriver>()
        coEvery { driver.send("hello", Effect.SCROLL) } returns true

        val registry = makeRegistry("main" to driver)
        runTest {
            val result = registry.route("main", "hello", Effect.SCROLL)
            result shouldBe true
            coVerify(exactly = 1) { driver.send("hello", Effect.SCROLL) }
        }
    }

    test("route to an unknown zone returns false without calling any driver") {
        val driver = mockk<ZoneDriver>()
        val registry = makeRegistry("main" to driver)

        runTest {
            val result = registry.route("ghost", "hello", Effect.SCROLL)
            result shouldBe false
            coVerify(exactly = 0) { driver.send(any(), any()) }
        }
    }

    test("broadcast calls all drivers in parallel and returns aggregate result") {
        val mainDriver = mockk<ZoneDriver>()
        val statusDriver = mockk<ZoneDriver>()
        coEvery { mainDriver.send(any(), any()) } returns true
        coEvery { statusDriver.send(any(), any()) } returns true

        val registry = makeRegistry("main" to mainDriver, "status" to statusDriver)
        runTest {
            val result = registry.broadcast("hello", Effect.SCROLL)
            result.successful shouldContain "main"
            result.successful shouldContain "status"
            result.failed shouldHaveSize 0
        }
    }

    test("broadcast: one zone failing does not block or remove other zone from successful list") {
        val goodDriver = mockk<ZoneDriver>()
        val badDriver = mockk<ZoneDriver>()
        coEvery { goodDriver.send(any(), any()) } returns true
        coEvery { badDriver.send(any(), any()) } throws RuntimeException("SPI timeout")

        val registry = makeRegistry("main" to goodDriver, "kitchen" to badDriver)
        runTest {
            val result = registry.broadcast("hello", Effect.SCROLL)
            result.successful shouldContain "main"
            result.successful.none { id: String -> id == "kitchen" } shouldBe true
            result.failed.any { f: com.anjo.model.FailedZone -> f.zoneId == "kitchen" } shouldBe true
        }
    }

    test("listAll returns one ZoneStatus per registered zone") {
        val driver1 = mockk<ZoneDriver>()
        val driver2 = mockk<ZoneDriver>()
        every { driver1.status() } returns ZoneStatus(id = "main", type = "MAX7219", status = "ONLINE")
        every { driver2.status() } returns ZoneStatus(id = "status", type = "OLED", status = "OFFLINE")

        val registry = makeRegistry("main" to driver1, "status" to driver2)
        val statuses = registry.listAll()

        statuses shouldHaveSize 2
        statuses.any { s: ZoneStatus -> s.id == "main" && s.status == "ONLINE" } shouldBe true
        statuses.any { s: ZoneStatus -> s.id == "status" && s.status == "OFFLINE" } shouldBe true
    }
})
