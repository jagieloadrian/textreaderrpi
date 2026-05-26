package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul

class StatusPage(
    private val displayType: String,
    private val hardwareAvailable: Boolean,
    private val isActive: Boolean,
    private val currentMessage: String?,
    private val error: String?,
) {
    fun render(): String {
        return BaseLayout.render(pageTitle = "Display Status", activePath = "/status") {
            pageContent()
        }
    }

    private fun FlowContent.pageContent() {
        h2 { +"Display Status" }
        ul {
            li { +"Driver: $displayType" }
            li { +"Hardware available: $hardwareAvailable" }
            li { +"Active: $isActive" }
            li { +"Current message: ${currentMessage ?: "-"}" }
        }
        if (error != null) {
            p { +"Error: $error" }
        }
    }
}

