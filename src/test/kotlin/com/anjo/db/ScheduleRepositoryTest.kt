package com.anjo.db

import com.anjo.model.Schedule
import com.anjo.model.ScheduleStatus
import com.anjo.model.TriggerType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

class ScheduleRepositoryTest : FunSpec({

    val repository = ScheduleRepository()

    beforeSpec {
        Database.connect("jdbc:h2:mem:test_schedules;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(SchedulesTable) }
    }

    beforeEach {
        transaction { SchedulesTable.deleteWhere { SchedulesTable.id.isNotNull() } }
    }

    val testSchedule: () -> Schedule = {
        Schedule(
            text = "hello",
            triggerType = TriggerType.RECURRING,
            triggerValue = "5m"
        )
    }

    test("insert and findById round-trip") {
        runTest {
            val inserted = repository.insert(testSchedule())
            inserted.id.shouldNotBeNull()
            inserted.id.isNotEmpty() shouldBe true

            val found = repository.findById(inserted.id)
            found.shouldNotBeNull()
            found.text shouldBe "hello"
            found.id shouldBe inserted.id
        }
    }

    test("findAll returns all records") {
        runTest {
            repository.insert(testSchedule())
            repository.insert(testSchedule().copy(text = "world"))

            val all = repository.findAll()
            all shouldHaveAtLeastSize 2
        }
    }

    test("delete removes record") {
        runTest {
            val inserted = repository.insert(testSchedule())
            val deleted = repository.delete(inserted.id)
            deleted shouldBe true

            val found = repository.findById(inserted.id)
            found.shouldBeNull()
        }
    }

    test("update changes text field") {
        runTest {
            val inserted = repository.insert(testSchedule())
            val updated = repository.update(inserted.id, inserted.copy(text = "updated"))
            updated.shouldNotBeNull()
            updated.text shouldBe "updated"

            val found = repository.findById(inserted.id)
            found.shouldNotBeNull()
            found.text shouldBe "updated"
        }
    }

    test("updateStatus changes status") {
        runTest {
            val inserted = repository.insert(testSchedule())
            repository.updateStatus(inserted.id, "DONE")

            val found = repository.findById(inserted.id)
            found.shouldNotBeNull()
            found.status shouldBe ScheduleStatus.DONE
        }
    }
})

