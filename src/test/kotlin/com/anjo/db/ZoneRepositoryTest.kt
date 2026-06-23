package com.anjo.db

import com.anjo.model.NetworkZone
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant

class ZoneRepositoryTest : FunSpec({

    val repository = ZoneRepository()

    beforeSpec {
        Database.connect("jdbc:h2:mem:test_zones;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(NetworkZonesTable) }
    }

    beforeEach {
        transaction { NetworkZonesTable.deleteWhere { NetworkZonesTable.id.isNotNull() } }
    }

    val testZone: () -> NetworkZone = {
        NetworkZone(
            id = "zone-kitchen",
            name = "Kitchen Display",
            ip = "192.168.1.50",
            type = "MAX7219",
            discoveryMethod = "MANUAL",
            createdAt = Instant.now().toString(),
            lastSeenAt = null
        )
    }

    test("should return zone in findAll after upsert") {
        runTest {
            repository.upsert(testZone())
            val all = repository.findAll()
            all shouldHaveSize 1
            all[0].id shouldBe "zone-kitchen"
        }
    }

    test("should update lastSeenAt on second upsert with same id") {
        runTest {
            repository.upsert(testZone())
            val updatedLastSeen = Instant.now().plusSeconds(60).toString()
            repository.upsert(testZone().copy(lastSeenAt = updatedLastSeen))

            val all = repository.findAll()
            all shouldHaveSize 1
            all[0].lastSeenAt shouldBe updatedLastSeen
        }
    }

    test("should survive restart — new repository instance returns previously inserted zone") {
        runTest {
            repository.upsert(testZone())
            val freshRepository = ZoneRepository()
            val found = freshRepository.findById("zone-kitchen")
            found.shouldNotBeNull()
            found.id shouldBe "zone-kitchen"
            found.name shouldBe "Kitchen Display"
        }
    }

    test("should remove zone after delete") {
        runTest {
            repository.upsert(testZone())
            repository.delete("zone-kitchen") shouldBe true
            repository.findById("zone-kitchen").shouldBeNull()
        }
    }

    test("should persist firmware zone with null ip and displaySubtype") {
        runTest {
            val firmwareZone = NetworkZone(
                id = "pico-salon",
                name = "pico-salon",
                ip = null,
                type = "FIRMWARE",
                discoveryMethod = "MANUAL",
                createdAt = Instant.now().toString(),
                lastSeenAt = null,
                displaySubtype = "SSD1306"
            )
            repository.upsert(firmwareZone)
            val found = repository.findById("pico-salon")
            found.shouldNotBeNull()
            found.ip.shouldBeNull()
            found.displaySubtype shouldBe "SSD1306"
        }
    }

    test("should persist network zone with non-null ip and null displaySubtype") {
        runTest {
            val networkZone = testZone()
            repository.upsert(networkZone)
            val found = repository.findById("zone-kitchen")
            found.shouldNotBeNull()
            found.ip shouldBe "192.168.1.50"
            found.displaySubtype.shouldBeNull()
        }
    }
})
