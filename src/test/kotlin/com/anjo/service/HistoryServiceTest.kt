package com.anjo.service

import com.anjo.db.HistoryRepository
import com.anjo.db.HistoryTable
import com.anjo.model.HistoryFilter
import com.anjo.model.HistoryRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

            val (items, total) = service.findPaginated(HistoryFilter(effect = null, source = null, zone = "zone-a", search = null), 1, 20)
            total shouldBe 2L
            items.all { it.zoneId == "zone-a" } shouldBe true
        }
    }

    test("should forward both effect and zone params returning only zone-a SCROLL rows") {
        runTest {
            repository.insert(HistoryRecord(text = "a-scroll", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "a-blink", effect = "BLINK", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "b-scroll", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-b"))

            val (items, total) = service.findPaginated(HistoryFilter(effect = "SCROLL", source = null, zone = "zone-a", search = null), 1, 20)
            total shouldBe 1L
            items[0].zoneId shouldBe "zone-a"
            items[0].effect shouldBe "SCROLL"
        }
    }

    test("exportCsv with no filter returns CSV with D-10 header row as first line") {
        runTest {
            repository.insert(HistoryRecord(text = "some text", effect = "SCROLL", source = "IMMEDIATE"))

            val csv = service.exportCsv(HistoryFilter(null, null, null, null))
            val firstLine = csv.lines().first()
            firstLine shouldBe "ID,Text,Effect,Source,Zone ID,Schedule ID,Displayed At,Webhook Status"
        }
    }

    test("exportCsv quotes field containing comma per RFC 4180") {
        runTest {
            repository.insert(HistoryRecord(text = "hello, world", effect = "SCROLL", source = "IMMEDIATE"))

            val csv = service.exportCsv(HistoryFilter(null, null, null, null))
            csv shouldContain "\"hello, world\""
        }
    }

    test("exportCsv with effect filter includes only matching rows") {
        runTest {
            repository.insert(HistoryRecord(text = "scroll-row", effect = "SCROLL", source = "IMMEDIATE"))
            repository.insert(HistoryRecord(text = "blink-row", effect = "BLINK", source = "IMMEDIATE"))

            val csv = service.exportCsv(HistoryFilter(effect = "SCROLL", source = null, zone = null, search = null))
            csv shouldContain "scroll-row"
            csv shouldNotContain "blink-row"
        }
    }
})
