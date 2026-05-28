package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.html
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.title

object BaseLayout {
    fun render(pageTitle: String, activePath: String, content: FlowContent.() -> Unit): String {
        return createHTML().html {
            attributes["data-theme"] = "dark"
            attributes["lang"] = "en"
            head {
                title(pageTitle)
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
            }
            body {
                header(classes = "container") {
                    h1 { +"TextReaderRpi" }
                    nav {
                        a(href = "/") { +label("/", "Home", activePath) }
                        span { +" | " }
                        a(href = "/status") { +label("/status", "Status", activePath) }
                        span { +" | " }
                        a(href = "/settings/display") { +label("/settings/display", "Settings", activePath) }
                        span { +" | " }
                        a(href = "/schedule") { +label("/schedule", "Schedule", activePath) }
                    }
                }
                main(classes = "container") { content() }
                footer(classes = "container") { +"TextReaderRpi" }
                script(src = "/static/app.js") {}
            }
        }
    }

    private fun label(path: String, text: String, activePath: String): String {
        return if (activePath == path) "$text *" else text
    }
}

