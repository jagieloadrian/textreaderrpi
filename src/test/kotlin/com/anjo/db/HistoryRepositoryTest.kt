package com.anjo.db

import com.anjo.model.HistoryFilter
import com.anjo.model.HistoryRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HistoryRepositoryTest : FunSpec({

    val repository = HistoryRepository()

    beforeSpec {
        Database.connect("jdbc:h2:mem:test_history;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(HistoryTable) }
    }

    beforeEach {
        transaction { HistoryTable.deleteWhere { HistoryTable.id.isNotNull() } }
    }

    fun makeRecord(text: String = "hello", effect: String = "SCROLL", source: String = "IMMEDIATE") =
        HistoryRecord(text = text, effect = effect, source = source)

    test("should insert and round-trip all columns including scheduleId, zoneId, and webhookStatus") {
        runTest {
            val record = HistoryRecord(
                text = "test text",
                effect = "BLINK",
                source = "SCHEDULED",
                scheduleId = "sched-123",
                zoneId = "zone-a",
                webhookStatus = "sent"
            )
            val inserted = repository.insert(record)

            inserted.id.isNotEmpty() shouldBe true
            inserted.displayedAt.isNotEmpty() shouldBe true
            inserted.text shouldBe "test text"
            inserted.effect shouldBe "BLINK"
            inserted.source shouldBe "SCHEDULED"
            inserted.scheduleId shouldBe "sched-123"
            inserted.zoneId shouldBe "zone-a"
            inserted.webhookStatus shouldBe "sent"

            val (items, total) = repository.findPaginated(HistoryFilter(null, null, null, null), 1, 20)
            total shouldBe 1L
            items[0].id shouldBe inserted.id
            items[0].scheduleId shouldBe "sched-123"
            items[0].zoneId shouldBe "zone-a"
            items[0].webhookStatus shouldBe "sent"

            val noStatusRecord = makeRecord()
            val insertedNoStatus = repository.insert(noStatusRecord)
            insertedNoStatus.webhookStatus shouldBe null
            val (items2, _) = repository.findPaginated(HistoryFilter(null, null, null, null), 1, 20)
            items2.find { it.id == insertedNoStatus.id }?.webhookStatus shouldBe null
        }
    }

    test("should prune oldest row when 1001st row is inserted leaving table at 1000") {
        runTest {
            repeat(1000) { i ->
                repository.insert(makeRecord(text = "row-$i"))
            }
            val (_, totalBefore) = repository.findPaginated(HistoryFilter(null, null, null, null), 1, 1)
            totalBefore shouldBe 1000L

            repository.insert(makeRecord(text = "row-1001"))
            val (_, totalAfter) = repository.findPaginated(HistoryFilter(null, null, null, null), 1, 1)
            totalAfter shouldBe 1000L
        }
    }

    test("should return different slices for page 1 vs page 2") {
        runTest {
            repeat(5) { i -> repository.insert(makeRecord(text = "page-item-$i")) }

            val (page1, _) = repository.findPaginated(HistoryFilter(null, null, null, null), 1, 3)
            val (page2, _) = repository.findPaginated(HistoryFilter(null, null, null, null), 2, 3)

            page1.size shouldBe 3
            page2.size shouldBe 2
            page1[0].id shouldNotBe page2[0].id
        }
    }

    test("should filter by effect returning only SCROLL records") {
        runTest {
            repository.insert(makeRecord(text = "scroll-1", effect = "SCROLL"))
            repository.insert(makeRecord(text = "blink-1", effect = "BLINK"))
            repository.insert(makeRecord(text = "scroll-2", effect = "SCROLL"))

            val (items, total) = repository.findPaginated(HistoryFilter(effect = "SCROLL", source = null, zone = null, search = null), 1, 20)
            total shouldBe 2L
            items.all { it.effect == "SCROLL" } shouldBe true
        }
    }

    test("should filter by source returning only IMMEDIATE records") {
        runTest {
            repository.insert(makeRecord(text = "imm-1", source = "IMMEDIATE"))
            repository.insert(makeRecord(text = "sched-1", source = "SCHEDULED"))
            repository.insert(makeRecord(text = "imm-2", source = "IMMEDIATE"))

            val (items, total) = repository.findPaginated(HistoryFilter(effect = null, source = "IMMEDIATE", zone = null, search = null), 1, 20)
            total shouldBe 2L
            items.all { it.source == "IMMEDIATE" } shouldBe true
        }
    }

    test("should filter by zone returning only matching-zone records") {
        runTest {
            repository.insert(HistoryRecord(text = "zone-a-1", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "zone-a-2", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "zone-b-1", effect = "BLINK", source = "IMMEDIATE", zoneId = "zone-b"))

            val (items, total) = repository.findPaginated(HistoryFilter(effect = null, source = null, zone = "zone-a", search = null), 1, 20)
            total shouldBe 2L
            items.all { it.zoneId == "zone-a" } shouldBe true
        }
    }

    test("should filter by effect and zone narrowing to matching rows only") {
        runTest {
            repository.insert(HistoryRecord(text = "a-scroll", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "a-blink", effect = "BLINK", source = "IMMEDIATE", zoneId = "zone-a"))
            repository.insert(HistoryRecord(text = "b-scroll", effect = "SCROLL", source = "IMMEDIATE", zoneId = "zone-b"))

            val (items, total) = repository.findPaginated(HistoryFilter(effect = "SCROLL", source = null, zone = "zone-a", search = null), 1, 20)
            total shouldBe 1L
            items[0].zoneId shouldBe "zone-a"
            items[0].effect shouldBe "SCROLL"
        }
    }

    test("search filter is case-insensitive and matches Hello row with hello term") {
        runTest {
            repository.insert(makeRecord(text = "Hello"))
            repository.insert(makeRecord(text = "World"))

            val (items, total) = repository.findPaginated(HistoryFilter(effect = null, source = null, zone = null, search = "hello"), 1, 20)
            total shouldBe 1L
            items[0].text shouldBe "Hello"
        }
    }

    test("search filter null returns all rows with no filtering") {
        runTest {
            repository.insert(makeRecord(text = "Hello"))
            repository.insert(makeRecord(text = "World"))

            val (items, total) = repository.findPaginated(HistoryFilter(effect = null, source = null, zone = null, search = null), 1, 20)
            total shouldBe 2L
            items.size shouldBe 2
        }
    }
})
