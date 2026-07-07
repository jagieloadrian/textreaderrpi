package com.anjo.routing.ui

import com.anjo.service.HistoryService
import com.anjo.service.ZoneRegistry
import com.anjo.validation.HistoryValidators
import com.anjo.web.templates.BaseLayout
import com.anjo.web.templates.historyPage
import com.anjo.web.templates.urlEncode
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_UI_SIZE = 1000

fun Route.historyUIRoutes(historyService: HistoryService, zoneRegistry: ZoneRegistry) {
    get("/history") {
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rawSize = call.request.queryParameters["size"] ?: "20"
        val sizeAll = rawSize.lowercase() == "all"
        val size = if (sizeAll) MAX_UI_SIZE else rawSize.toIntOrNull()?.coerceIn(1, MAX_UI_SIZE) ?: 20
        val effect = call.request.queryParameters["effect"].orEmpty()
        val source = call.request.queryParameters["source"].orEmpty()
        val zone = call.request.queryParameters["zone"].orEmpty()
        val expandAll = call.request.queryParameters["expand"] == "all"
        val rawSearch = call.request.queryParameters["search"].orEmpty()
        val filter = HistoryValidators.parseFilter(call.request.queryParameters)
        val (items, total) = historyService.findPaginated(filter, page, size)
        val sanitizedSearch = HistoryValidators.sanitizeSearchTerm(rawSearch)
        val exportHref = "/api/v1/history/export?effect=${effect.urlEncode()}&source=${source.urlEncode()}&zone=${zone.urlEncode()}&search=${sanitizedSearch.urlEncode()}"
        val html = BaseLayout.render(pageTitle = "History — TextReaderRpi", activePath = "/history") {
            historyPage(items, page, rawSize, total, expandAll, effect, source, zone, zoneRegistry.listAll(), rawSearch, exportHref)
        }
        call.respondText(html, ContentType.Text.Html)
    }
}
