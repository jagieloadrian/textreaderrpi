package com.anjo.web.templates

import com.anjo.model.HistoryRecord
import com.anjo.model.ZoneStatus
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
import kotlinx.html.ul

fun FlowContent.historyPage(
    items: List<HistoryRecord>,
    page: Int,
    rawSize: String,
    total: Long,
    expandAll: Boolean,
    effect: String,
    source: String,
    zone: String,
    zones: List<ZoneStatus>,
    search: String = "",
    exportHref: String = ""
) {
    h2 { +"Display History" }

    form {
        method = FormMethod.get
        action = "/history"
        label { htmlFor = "search"; +"Search" }
        input {
            type = InputType.text
            id = "search"
            name = "search"
            placeholder = "Search displayed text…"
            value = search
        }
        label { htmlFor = "effect"; +"Effect" }
        select {
            id = "effect"; name = "effect"
            option { value = "ALL"; if (effect.isEmpty() || effect == "ALL") selected = true; +"All effects" }
            option { value = "SCROLL"; if (effect == "SCROLL") selected = true; +"SCROLL" }
            option { value = "BLINK"; if (effect == "BLINK") selected = true; +"BLINK" }
            option { value = "REVERSE"; if (effect == "REVERSE") selected = true; +"REVERSE" }
            option { value = "FADE"; if (effect == "FADE") selected = true; +"FADE" }
        }
        label { htmlFor = "source"; +"Source" }
        select {
            id = "source"; name = "source"
            option { value = "ALL"; if (source.isEmpty() || source == "ALL") selected = true; +"All sources" }
            option { value = "IMMEDIATE"; if (source == "IMMEDIATE") selected = true; +"IMMEDIATE" }
            option { value = "SCHEDULED"; if (source == "SCHEDULED") selected = true; +"SCHEDULED" }
        }
        label { htmlFor = "size"; +"Page size" }
        select {
            id = "size"; name = "size"
            option { value = "20"; if (rawSize == "20" || rawSize.isEmpty()) selected = true; +"20" }
            option { value = "50"; if (rawSize == "50") selected = true; +"50" }
            option { value = "all"; if (rawSize.lowercase() == "all") selected = true; +"all" }
        }
        label { htmlFor = "zone"; +"Zone" }
        select {
            id = "zone"; name = "zone"
            option { value = "ALL"; if (zone.isEmpty() || zone == "ALL") selected = true; +"All zones" }
            zones.forEach { z ->
                option { value = z.id; if (zone == z.id) selected = true; +z.id }
            }
        }
        if (expandAll) {
            input {
                type = InputType.hidden
                name = "expand"
                value = "all"
            }
        }
        button { type = ButtonType.submit; +"Apply Filters" }
        if (exportHref.isNotEmpty()) {
            a(href = exportHref) {
                attributes["role"] = "button"
                attributes["class"] = "secondary outline"
                +"Export CSV"
            }
        }
        div {
            attributes["class"] = "history-expand-btns"
            button {
                type = ButtonType.button
                attributes["data-expand-url"] = "?page=$page&expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}&search=${search.urlEncode()}"
                attributes["class"] = if (expandAll) "secondary" else "secondary outline"
                +"Expand all"
            }
            button {
                type = ButtonType.button
                attributes["data-expand-url"] = "?page=$page&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}&zone=${zone.urlEncode()}&search=${search.urlEncode()}"
                attributes["class"] = if (!expandAll) "secondary" else "secondary outline"
                +"Collapse all"
            }
        }
    }

    if (items.isEmpty()) {
        p { strong { +"No display history yet." } }
        p { +"Send text to the display to start recording events." }
    } else {
        div {
            attributes["class"] = "history-grid"
            for (item in items) {
                val shortDate = item.displayedAt.replace('T', ' ').take(19)
                details {
                    if (expandAll) attributes["open"] = ""
                    summary {
                        strong { +(item.text.take(50).let { if (item.text.length > 50) "$it…" else it }) }
                        span { attributes["class"] = "history-meta"; +"${item.effect} · $shortDate" }
                    }
                    p { strong { +"Text:" }; +" "; highlightText(item.text, search.takeIf { it.isNotBlank() })() }
                    p { strong { +"Effect:" }; +" ${item.effect}" }
                    p { strong { +"Source:" }; +" ${item.source}" }
                    if (item.source == "SCHEDULED" && item.scheduleId != null) {
                        p { strong { +"Schedule:" }; +" ${item.scheduleId.take(8)}" }
                    }
                    if (item.zoneId != null) {
                        p { strong { +"Zone:" }; +" ${item.zoneId}" }
                    }
                    if (item.webhookStatus != null) {
                        p { strong { +"Webhook:" }; +" ${item.webhookStatus}" }
                    }
                    p { strong { +"Displayed at:" }; +" $shortDate" }
                }
            }
        }
    }

    if (rawSize.lowercase() != "all") {
        val sizeInt = rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
        if (total > sizeInt) {
            val pageCount = (total + sizeInt - 1) / sizeInt
            val start = maxOf(1L, page.toLong() - 5L)
            val end = minOf(pageCount, page.toLong() + 5L)
            nav {
                attributes["aria-label"] = "Pagination"
                ul {
                    for (p in start..end) {
                        li {
                            a(href = "?page=$p&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}${if (expandAll) "&expand=all" else ""}&zone=${zone.urlEncode()}&search=${search.urlEncode()}") {
                                if (p == page.toLong()) attributes["aria-current"] = "page"
                                +"$p"
                            }
                        }
                    }
                }
            }
        }
    }
}
