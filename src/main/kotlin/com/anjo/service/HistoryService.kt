package com.anjo.service

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryFilter
import com.anjo.model.HistoryRecord
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter

class HistoryService(private val repository: HistoryRepository) {

    suspend fun findPaginated(filter: HistoryFilter, page: Int, size: Int): Pair<List<HistoryRecord>, Long> =
        repository.findPaginated(filter, page, size)

    suspend fun exportCsv(filter: HistoryFilter): String {
        val records = repository.findAll(filter)
        val header = listOf("ID", "Text", "Effect", "Source", "Zone ID", "Schedule ID", "Displayed At", "Webhook Status")
        val rows = records.map { r ->
            listOf(r.id, r.text, r.effect, r.source, r.zoneId.orEmpty(), r.scheduleId.orEmpty(), r.displayedAt, r.webhookStatus.orEmpty())
        }
        return csvWriter().writeAllAsString(listOf(header) + rows)
    }
}
