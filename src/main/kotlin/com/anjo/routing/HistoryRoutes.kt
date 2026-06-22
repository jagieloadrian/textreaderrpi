package com.anjo.routing

import com.anjo.model.HistoryFilter
import com.anjo.model.HistoryPageResponse
import com.anjo.service.HistoryService
import com.anjo.validation.HistoryValidators
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.historyRoutes(historyService: HistoryService) {
    get("/history") {
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val zone = call.request.queryParameters["zone"]?.takeIf { it.isNotEmpty() && it != "ALL" }
        val search = HistoryValidators.sanitizeSearchTerm(call.request.queryParameters["search"].orEmpty()).takeIf { it.isNotBlank() }
        val filter = HistoryFilter(effect = effect, source = source, zone = zone, search = search)
        val (items, total) = historyService.findPaginated(filter, page, size)
        call.respond(HistoryPageResponse(items, page, size, total))
    }

    get("/history/export") {
        val effect = call.request.queryParameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val source = call.request.queryParameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val zone = call.request.queryParameters["zone"]?.takeIf { it.isNotEmpty() && it != "ALL" }
        val search = HistoryValidators.sanitizeSearchTerm(call.request.queryParameters["search"].orEmpty()).takeIf { it.isNotBlank() }
        val filter = HistoryFilter(effect = effect, source = source, zone = zone, search = search)
        val csv = historyService.exportCsv(filter)
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "history.csv").toString()
        )
        call.respondText(csv, ContentType("text", "csv"))
    }
}
