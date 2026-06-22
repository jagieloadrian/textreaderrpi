package com.anjo.validation

object HistoryValidators {
    fun sanitizeSearchTerm(input: String): String = input.replace("%", "").replace("_", "")
}
