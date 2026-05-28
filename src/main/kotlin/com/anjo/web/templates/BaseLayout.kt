package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.html
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import kotlinx.html.ul

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
                        ul {
                            li { a(href = "/") { attributes["aria-current"] = if (activePath == "/") "page" else ""; +"Home" } }
                            li { a(href = "/schedule") { attributes["aria-current"] = if (activePath == "/schedule") "page" else ""; +"Schedule" } }
                            li { a(href = "/settings/display") { attributes["aria-current"] = if (activePath == "/settings/display") "page" else ""; +"Settings" } }
                            li { a(href = "/status") { attributes["aria-current"] = if (activePath == "/status") "page" else ""; +"Status" } }
                        }
                    }
                }
                main(classes = "container") { content() }
                footer(classes = "container") { +"TextReaderRpi" }
                script(src = "/static/app.js") {}
            }
        }
    }
}
