package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.article
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.span

object StatusPage {
    fun render(): String = BaseLayout.render(pageTitle = "Display Status", activePath = "/status") {
        pageContent()
    }

    private fun FlowContent.pageContent() {
        article {
            h2 { +"System" }
            p { +"Uptime: "; span { id = "status-uptime"; +"Loading..." } }
            p { +"Memory Used: "; span { id = "status-memory-used"; +"Loading..." } }
            p { +"Memory Max: "; span { id = "status-memory-max"; +"Loading..." } }
        }
        article {
            h2 { +"Display" }
            p { +"Status: "; span { id = "status-display"; +"Loading..." } }
            p { +"Total Failures: "; span { id = "status-failures"; +"Loading..." } }
        }
        article {
            h2 { +"Hardware Metrics" }
            p { +"Display Failures: "; span { id = "status-hw-failures"; +"Loading..." } }
            p { +"Recovery Retries: "; span { id = "status-hw-retries"; +"Loading..." } }
        }
    }
}
