package com.anjo.service

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryRecord

class HistoryService(private val repository: HistoryRepository) {

    suspend fun findPaginated(
        page: Int,
        size: Int,
        effect: String? = null,
        source: String? = null,
        zone: String? = null
    ): Pair<List<HistoryRecord>, Long> = repository.findPaginated(page, size, effect, source, zone)
}
