package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select

class SettingsPage(
    private val selectedType: String,
) {
    fun render(): String {
        return BaseLayout.render(pageTitle = "Display Settings", activePath = "/settings/display") {
            pageContent()
        }
    }

    private fun FlowContent.pageContent() {
        h2 { +"Display Settings" }
        p { +"Select active display driver" }
        select {
            id = "driverSelect"
            option {
                value = "max7219"
                if (selectedType.equals("MAX7219", ignoreCase = true)) selected = true
                +"MAX7219"
            }
            option {
                value = "lcd"
                if (selectedType.equals("LCD", ignoreCase = true)) selected = true
                +"LCD"
            }
            option {
                value = "oled"
                if (selectedType.equals("OLED", ignoreCase = true)) selected = true
                +"OLED"
            }
        }
        button {
            id = "applyDriverBtn"
            +"Apply"
        }
    }
}

