package com.anjo.routing.ui

import com.anjo.db.HistoryRepository
import com.anjo.web.templates.BaseLayout
import com.anjo.web.templates.historyPage
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_UI_SIZE = 1000L

fun Route.historyUIRoutes(historyRepository: HistoryRepository) {
    get("/history") {
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rawSize = call.request.queryParameters["size"] ?: "20"
        val sizeAll = rawSize.lowercase() == "all"
        val size = if (sizeAll) MAX_UI_SIZE.toInt() else rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val effect = call.request.queryParameters["effect"].orEmpty()
        val source = call.request.queryParameters["source"].orEmpty()
        val expandAll = call.request.queryParameters["expand"] == "all"
        val effectFilter = effect.takeIf { it.isNotEmpty() && it != "ALL" }
        val sourceFilter = source.takeIf { it.isNotEmpty() && it != "ALL" }
        val (items, total) = if (sizeAll) {
            val (_, t) = historyRepository.findPaginated(1, 1, effectFilter, sourceFilter)
            historyRepository.findPaginated(1, t.coerceAtMost(MAX_UI_SIZE).toInt().coerceAtLeast(1), effectFilter, sourceFilter)
        } else {
            historyRepository.findPaginated(page, size, effectFilter, sourceFilter)
        }
        val html = BaseLayout.render(pageTitle = "History — TextReaderRpi", activePath = "/history") {
            historyPage(items, page, rawSize, total, expandAll, effect, source)
        }
        call.respondText(html, ContentType.Text.Html)
    }
}
