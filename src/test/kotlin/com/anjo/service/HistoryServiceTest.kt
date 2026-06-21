package com.anjo.service

import com.anjo.db.HistoryRepository
import com.anjo.db.HistoryTable
import com.anjo.model.HistoryRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HistoryServiceTest : FunSpec({

    val repository = HistoryRepository()
    val service = HistoryService(repository)

    beforeSpec {
        Database.connect("jdbc:h2:mem:test_history_service;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(HistoryTable) }
    }

    beforeEach {
        transaction { HistoryTable.deleteWhere { HistoryTable.id.isNotNull() } }
    }

    test("should return only zone-a rows when zone param is zone-a") {
        runTest {
            repository.insert(HistoryRecord(text = "a-1", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "a-2", effect = "BLINK", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "b-1", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-b"))

            val (items, total) = service.findPaginated(1, 20, zone = "zone-a")
            total shouldBe 2L
            items.all { it.zoneId == "zone-a" } shouldBe true
        }
    }

    test("should forward both effect and zone params returning only zone-a SCROLL rows") {
        runTest {
            repository.insert(HistoryRecord(text = "a-scroll", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "a-blink", effect = "BLINK", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "b-scroll", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-b"))

            val (items, total) = service.findPaginated(1, 20, effect = "SCROLL", zone = "zone-a")
            total shouldBe 1L
            items[0].zoneId shouldBe "zone-a"
            items[0].effect shouldBe "SCROLL"
        }
    }
})
