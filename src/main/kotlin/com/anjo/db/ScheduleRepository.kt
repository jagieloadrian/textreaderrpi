package com.anjo.db

import com.anjo.model.Effect
import com.anjo.model.Schedule
import com.anjo.model.ScheduleStatus
import com.anjo.model.TriggerType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID

class ScheduleRepository {

    suspend fun findAll(): List<Schedule> = suspendTransaction {
        SchedulesTable.selectAll().map { it.toSchedule() }
    }

    suspend fun findById(id: String): Schedule? = suspendTransaction {
        SchedulesTable.selectAll()
            .where { SchedulesTable.id eq id }
            .map { it.toSchedule() }
            .singleOrNull()
    }

    suspend fun findAllActive(): List<Schedule> = suspendTransaction {
        SchedulesTable.selectAll()
            .where { SchedulesTable.status eq "ACTIVE" }
            .map { it.toSchedule() }
    }

    suspend fun insert(schedule: Schedule): Schedule {
        val newId = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        suspendTransaction {
            SchedulesTable.insert {
                it[id] = newId
                it[text] = schedule.text
                it[triggerType] = schedule.triggerType.name
                it[triggerValue] = schedule.triggerValue
                it[effect] = schedule.effect.name
                it[priority] = schedule.priority
                it[maxRuns] = schedule.maxRuns
                it[expiresAt] = schedule.expiresAt
                it[SchedulesTable.createdAt] = createdAt
                it[status] = schedule.status.name
            }
        }
        return schedule.copy(id = newId, createdAt = createdAt)
    }

    suspend fun update(id: String, schedule: Schedule): Schedule? {
        val updated = suspendTransaction {
            SchedulesTable.update({ SchedulesTable.id eq id }) {
                it[text] = schedule.text
                it[triggerType] = schedule.triggerType.name
                it[triggerValue] = schedule.triggerValue
                it[effect] = schedule.effect.name
                it[priority] = schedule.priority
                it[maxRuns] = schedule.maxRuns
                it[expiresAt] = schedule.expiresAt
                it[status] = schedule.status.name
            }
        }
        return if (updated > 0) findById(id) else null
    }

    suspend fun updateStatus(id: String, status: String) {
        suspendTransaction {
            SchedulesTable.update({ SchedulesTable.id eq id }) {
                it[SchedulesTable.status] = status
            }
        }
    }

    suspend fun delete(id: String): Boolean {
        val deletedRows = suspendTransaction {
            SchedulesTable.deleteWhere { SchedulesTable.id eq id }
        }
        return deletedRows > 0
    }

    private fun ResultRow.toSchedule(): Schedule = Schedule(
        id = this[SchedulesTable.id],
        text = this[SchedulesTable.text],
        triggerType = TriggerType.valueOf(this[SchedulesTable.triggerType]),
        triggerValue = this[SchedulesTable.triggerValue],
        effect = Effect.valueOf(this[SchedulesTable.effect]),
        priority = this[SchedulesTable.priority],
        maxRuns = this[SchedulesTable.maxRuns],
        expiresAt = this[SchedulesTable.expiresAt],
        createdAt = this[SchedulesTable.createdAt],
        status = ScheduleStatus.valueOf(this[SchedulesTable.status])
    )
}
