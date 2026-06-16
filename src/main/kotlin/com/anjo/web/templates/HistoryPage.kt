package com.anjo.web.templates

import com.anjo.model.HistoryRecord
import java.net.URLEncoder
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
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

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

fun FlowContent.historyPage(
    items: List<HistoryRecord>,
    page: Int,
    rawSize: String,
    total: Long,
    expandAll: Boolean,
    effect: String,
    source: String
) {
    h2 { +"Display History" }

    form {
        method = FormMethod.get
        action = "/history"
        label { htmlFor = "effect"; +"Effect" }
        select {
            id = "effect"; name = "effect"
            option { value = "ALL"; if (effect.isEmpty() || effect == "ALL") selected = true; +"ALL" }
            option { value = "SCROLL"; if (effect == "SCROLL") selected = true; +"SCROLL" }
            option { value = "BLINK"; if (effect == "BLINK") selected = true; +"BLINK" }
            option { value = "REVERSE"; if (effect == "REVERSE") selected = true; +"REVERSE" }
            option { value = "FADE"; if (effect == "FADE") selected = true; +"FADE" }
        }
        label { htmlFor = "source"; +"Source" }
        select {
            id = "source"; name = "source"
            option { value = "ALL"; if (source.isEmpty() || source == "ALL") selected = true; +"ALL" }
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
        label { htmlFor = "zone"; +"Zone (Multi-zone — Phase 11)" }
        select {
            id = "zone"; name = "zone"
            attributes["disabled"] = ""
            option { +"Multi-zone — Phase 11" }
        }
        if (expandAll) {
            input {
                type = InputType.hidden
                name = "expand"
                value = "all"
            }
        }
        button { type = ButtonType.submit; +"Apply Filters" }
        a(href = "?expand=all&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}") { +"Expand all" }
        a(href = "?effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}") { +"Collapse all" }
    }

    if (items.isEmpty()) {
        p { +"No display events recorded yet. Send text via the home page to see history here." }
    } else {
        for (item in items) {
            details {
                if (expandAll) attributes["open"] = ""
                summary {
                    +(item.text.take(60).let { if (item.text.length > 60) "$it…" else it })
                    span { attributes["role"] = "note"; +item.effect }
                    +item.displayedAt
                }
                p { strong { +"Full text:" }; +item.text }
                p { strong { +"Effect:" }; +item.effect }
                p { strong { +"Source:" }; +item.source }
                if (item.source == "SCHEDULED" && item.scheduleId != null) {
                    p { strong { +"Schedule:" }; a(href = "/schedule?id=${item.scheduleId}") { +item.scheduleId.take(8) } }
                }
                if (item.zoneId != null) {
                    p { strong { +"Zone:" }; +item.zoneId }
                }
                if (item.webhookStatus != null) {
                    p { strong { +"Webhook:" }; +item.webhookStatus }
                }
                p { strong { +"Displayed at:" }; +item.displayedAt }
            }
        }
    }

    if (rawSize.lowercase() != "all") {
        val sizeInt = rawSize.toIntOrNull()?.coerceAtLeast(1) ?: 20
        if (total > sizeInt) {
            val pageCount = ((total + sizeInt - 1) / sizeInt).toInt()
            nav {
                attributes["aria-label"] = "Pagination"
                ul {
                    for (p in 1..pageCount) {
                        li {
                            a(href = "?page=$p&effect=${effect.urlEncode()}&source=${source.urlEncode()}&size=${rawSize.urlEncode()}${if (expandAll) "&expand=all" else ""}") {
                                if (p == page) attributes["aria-current"] = "page"
                                +"$p"
                            }
                        }
                    }
                }
            }
        }
    }
}
