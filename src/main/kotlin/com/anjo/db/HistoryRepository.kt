package com.anjo.db

import com.anjo.model.HistoryFilter
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
                    .singleOrNull()?.get(HistoryTable.id)
                if (oldest != null) {
                    HistoryTable.deleteWhere { HistoryTable.id eq oldest }
                }
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

    suspend fun findPaginated(filter: HistoryFilter, page: Int, size: Int): Pair<List<HistoryRecord>, Long> =
        suspendTransaction {
            val conditions = buildList {
                filter.effect?.let { add(HistoryTable.effect eq it) }
                filter.source?.let { add(HistoryTable.displaySource eq it) }
                filter.zone?.let { add(HistoryTable.zoneId eq it) }
                filter.search?.let { term ->
                    add(HistoryTable.text.lowerCase() like "%${term.lowercase()}%")
                }
            }
            var query = HistoryTable.selectAll()
            if (conditions.isNotEmpty()) {
                query = query.where { conditions.reduce { acc, op -> acc and op } }
            }
            val total = query.count()
            val items = query
                .orderBy(HistoryTable.displayedAt to SortOrder.DESC)
                .limit(size)
                .offset(((page - 1).toLong() * size))
                .map { it.toHistoryRecord() }
            Pair(items, total)
        }

    suspend fun findAll(filter: HistoryFilter): List<HistoryRecord> =
        suspendTransaction {
            val conditions = buildList {
                filter.effect?.let { add(HistoryTable.effect eq it) }
                filter.source?.let { add(HistoryTable.displaySource eq it) }
                filter.zone?.let { add(HistoryTable.zoneId eq it) }
                filter.search?.let { term ->
                    add(HistoryTable.text.lowerCase() like "%${term.lowercase()}%")
                }
            }
            var query = HistoryTable.selectAll()
            if (conditions.isNotEmpty()) {
                query = query.where { conditions.reduce { acc, op -> acc and op } }
            }
            query
                .orderBy(HistoryTable.displayedAt to SortOrder.DESC)
                .map { it.toHistoryRecord() }
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
