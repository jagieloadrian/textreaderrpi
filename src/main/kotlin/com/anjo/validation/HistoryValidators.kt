package com.anjo.validation

import com.anjo.model.HistoryFilter
import io.ktor.http.Parameters

object HistoryValidators {
    fun sanitizeSearchTerm(input: String): String = input.replace("%", "").replace("_", "")

    fun parseFilter(parameters: Parameters): HistoryFilter {
        val effect = parameters["effect"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val source = parameters["source"]?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val zone = parameters["zone"]?.takeIf { it.isNotEmpty() && it != "ALL" }
        val search = sanitizeSearchTerm(parameters["search"].orEmpty()).takeIf { it.isNotBlank() }
        return HistoryFilter(effect = effect, source = source, zone = zone, search = search)
    }
}
