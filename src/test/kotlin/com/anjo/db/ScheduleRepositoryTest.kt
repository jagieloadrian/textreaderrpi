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
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
        Schedule(text = "hello", triggerType = TriggerType.RECURRING, triggerValue = "5m")
    }

    test("should find inserted schedule by id") {
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

    test("should return all inserted records") {
        runTest {
            repository.insert(testSchedule())
            repository.insert(testSchedule().copy(text = "world"))
            repository.findAll() shouldHaveAtLeastSize 2
        }
    }

    test("should remove record after delete") {
        runTest {
            val inserted = repository.insert(testSchedule())
            repository.delete(inserted.id) shouldBe true
            repository.findById(inserted.id).shouldBeNull()
        }
    }

    test("should update text field") {
        runTest {
            val inserted = repository.insert(testSchedule())
            val updated = repository.update(inserted.id, inserted.copy(text = "updated"))
            updated.shouldNotBeNull()
            updated.text shouldBe "updated"
            repository.findById(inserted.id)?.text shouldBe "updated"
        }
    }

    test("should change schedule status to DONE") {
        runTest {
            val inserted = repository.insert(testSchedule())
            repository.updateStatus(inserted.id, "DONE")
            repository.findById(inserted.id)?.status shouldBe ScheduleStatus.DONE
        }
    }
})
