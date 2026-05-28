package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.textArea

class IndexPage {
    fun render(): String {
        return BaseLayout.render(pageTitle = "Text Input", activePath = "/") {
            pageContent()
        }
    }

    private fun FlowContent.pageContent() {
        h2 { +"Send Text" }
        div {
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
    }
}
