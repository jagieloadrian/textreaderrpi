package com.anjo.routing

import com.anjo.db.HistoryRepository
import com.anjo.model.HistoryPageResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.historyRoutes(historyRepository: HistoryRepository) {
    get("/history") {
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val (items, total) = historyRepository.findPaginated(page, size, effect, source)
        call.respond(HistoryPageResponse(items, page, size, total))
    }
}
