package com.anjo.db

import com.anjo.model.HistoryRecord
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID

class HistoryRepository {

    suspend fun insert(record: HistoryRecord): HistoryRecord {
        val newId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        suspendTransaction {
            val count = HistoryTable.selectAll().count()
            if (count >= MAX_ROWS) {
                val oldest = HistoryTable.selectAll()
                    .orderBy(HistoryTable.displayedAt to SortOrder.ASC)
                    .limit(1)
                    .singleOrNull()?.get(HistoryTable.id) ?: return@suspendTransaction
                HistoryTable.deleteWhere { HistoryTable.id eq oldest }
            }
            HistoryTable.insert {
                it[id] = newId
                it[text] = record.text
                it[effect] = record.effect
                it[displaySource] = record.source
                it[scheduleId] = record.scheduleId
                it[zoneId] = record.zoneId
                it[displayedAt] = now
                it[webhookStatus] = record.webhookStatus
            }
        }
        return record.copy(id = newId, displayedAt = now)
    }

    suspend fun findPaginated(
        page: Int,
        size: Int,
        effect: String? = null,
        source: String? = null
    ): Pair<List<HistoryRecord>, Long> = suspendTransaction {
        var query = HistoryTable.selectAll()
        if (effect != null) {
            query = query.where { HistoryTable.effect eq effect }
        }
        if (source != null) {
            if (effect != null) {
                query = query.andWhere { HistoryTable.displaySource eq source }
            } else {
                query = query.where { HistoryTable.displaySource eq source }
            }
        }
        val total = query.count()
        val items = query
            .orderBy(HistoryTable.displayedAt to SortOrder.DESC)
            .limit(size)
            .offset(((page - 1).toLong() * size))
            .map { it.toHistoryRecord() }
        Pair(items, total)
    }

    private fun ResultRow.toHistoryRecord(): HistoryRecord = HistoryRecord(
        id = this[HistoryTable.id],
        text = this[HistoryTable.text],
        effect = this[HistoryTable.effect],
        source = this[HistoryTable.displaySource],
        scheduleId = this[HistoryTable.scheduleId],
        zoneId = this[HistoryTable.zoneId],
        displayedAt = this[HistoryTable.displayedAt],
        webhookStatus = this[HistoryTable.webhookStatus]
    )

    companion object {
        private const val MAX_ROWS = 1000L
    }
}
