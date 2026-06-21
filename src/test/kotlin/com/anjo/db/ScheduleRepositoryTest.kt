package com.anjo.db

import com.anjo.model.ConflictPolicy
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
import java.time.Instant

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

    test("should round-trip conflictPolicy webhookUrl and zoneId fields") {
        runTest {
            val schedule = Schedule(
                text = "webhook test",
                triggerType = TriggerType.RECURRING,
                triggerValue = "10m",
                conflictPolicy = ConflictPolicy.SKIP_NEW,
                webhookUrl = "https://example.com/hook",
                zoneId = "zone-a"
            )
            val inserted = repository.insert(schedule)
            val found = repository.findById(inserted.id)
            found.shouldNotBeNull()
            found.conflictPolicy shouldBe ConflictPolicy.SKIP_NEW
            found.webhookUrl shouldBe "https://example.com/hook"
            found.zoneId shouldBe "zone-a"
        }
    }

    test("should exclude ONESHOT row from findAllActive when firedAt is set") {
        runTest {
            val oneshotSchedule = Schedule(
                text = "oneshot fired",
                triggerType = TriggerType.ONESHOT,
                triggerValue = Instant.now().plusSeconds(3600).toString()
            )
            val inserted = repository.insert(oneshotSchedule)
            repository.updateFiredAtAndDone(inserted.id, Instant.now().toString())

            val active = repository.findAllActive()
            active.none { it.id == inserted.id } shouldBe true
        }
    }

    test("should include ONESHOT row in findAllActive when firedAt is null") {
        runTest {
            val oneshotSchedule = Schedule(
                text = "oneshot pending",
                triggerType = TriggerType.ONESHOT,
                triggerValue = Instant.now().plusSeconds(3600).toString()
            )
            val inserted = repository.insert(oneshotSchedule)

            val active = repository.findAllActive()
            active.any { it.id == inserted.id } shouldBe true
        }
    }

    test("should set firedAt and status DONE atomically via updateFiredAtAndDone") {
        runTest {
            val inserted = repository.insert(testSchedule())
            val ts = Instant.now().toString()
            repository.updateFiredAtAndDone(inserted.id, ts)

            val found = repository.findById(inserted.id)
            found.shouldNotBeNull()
            found.firedAt shouldBe ts
            found.status shouldBe ScheduleStatus.DONE
        }
    }

    test("should include RECURRING row in findAllActive regardless of firedAt") {
        runTest {
            val recurringSchedule = Schedule(
                text = "recurring active",
                triggerType = TriggerType.RECURRING,
                triggerValue = "5m"
            )
            val inserted = repository.insert(recurringSchedule)
            val active = repository.findAllActive()
            active.any { it.id == inserted.id } shouldBe true
        }
    }
})
