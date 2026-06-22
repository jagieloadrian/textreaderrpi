package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.mark

fun highlightText(text: String, term: String?): FlowContent.() -> Unit = {
    if (term.isNullOrEmpty()) {
        +text
    } else {
        var start = 0
        while (true) {
            val idx = text.indexOf(term, startIndex = start, ignoreCase = true)
            if (idx == -1) {
                +text.substring(start)
                break
            }
            +text.substring(start, idx)
            mark { +text.substring(idx, idx + term.length) }
            start = idx + term.length
        }
    }
}
