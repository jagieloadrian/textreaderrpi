package com.anjo.web.templates

import com.anjo.model.ZoneStatus
import kotlinx.html.FlowContent
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea

class IndexPage(private val zones: List<ZoneStatus>) {
    fun render(): String {
        return BaseLayout.render(pageTitle = "Text Input", activePath = "/") {
            pageContent()
        }
    }

    private fun FlowContent.pageContent() {
        article {
            h2 { +"Send Text" }
            label {
                htmlFor = "zoneSelect"
                +"Zone"
            }
            select {
                id = "zoneSelect"
                name = "zone"
                option { value = ""; selected = true; +"All zones" }
                zones.forEach { zone ->
                    option {
                        value = zone.id
                        +(if (zone.status == "OFFLINE") "${zone.id} (OFFLINE)" else zone.id)
                    }
                }
            }
            label {
                htmlFor = "textInput"
                +"Text"
            }
            textArea {
                id = "textInput"
                name = "text"
                rows = "5"
                maxLength = "128"
                placeholder = "Type message..."
            }
            p {
                id = "charCounter"
                +"0 / 128"
            }
            div {
                label {
                    htmlFor = "effectSelect"
                    +"Effect"
                }
                select {
                    id = "effectSelect"
                    name = "effect"
                    option { value = "SCROLL"; selected = true; +"Scroll" }
                    option { value = "BLINK"; +"Blink" }
                    option { value = "REVERSE"; +"Reverse" }
                    option { value = "FADE"; +"Fade" }
                }
            }
            button {
                id = "submitTextBtn"
                +"Send"
            }
        }
        article {
            h2 { +"Preview" }
            div {
                id = "effectPreview"
                attributes["class"] = "effect-preview"
                attributes["style"] = "visibility: hidden"
                span { id = "effectPreviewText" }
            }
        }
    }
}
