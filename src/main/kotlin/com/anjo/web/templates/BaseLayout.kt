package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
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
    fun render(pageTitle: String, activePath: String, headExtra: (HEAD.() -> Unit)? = null, content: FlowContent.() -> Unit): String {
        return createHTML().html {
            attributes["lang"] = "en"
            head {
                title(pageTitle)
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
                link(rel = "stylesheet", href = "/static/custom.css")
                headExtra?.invoke(this)
            }
            body {
                div {
                    attributes["class"] = "top-bar"
                    +"TextReaderRpi"
                    button {
                        id = "navToggle"
                        +"☰"
                    }
                }
                aside {
                    h1 { +"TextReaderRpi" }
                    nav {
                        ul {
                            li { a(href = "/") { attributes["aria-current"] = if (activePath == "/") "page" else ""; +"Send Text" } }
                            li { a(href = "/schedule") { attributes["aria-current"] = if (activePath == "/schedule") "page" else ""; +"Schedules" } }
                            li { a(href = "/history") { attributes["aria-current"] = if (activePath == "/history") "page" else ""; +"History" } }
                            li { a(href = "/zones") { attributes["aria-current"] = if (activePath == "/zones") "page" else ""; +"Zones" } }
                            li { a(href = "/status") { attributes["aria-current"] = if (activePath == "/status") "page" else ""; +"Status" } }
                        }
                    }
                }
                div {
                    id = "navBackdrop"
                    attributes["class"] = "nav-backdrop"
                }
                main(classes = "container") { content() }
                footer(classes = "container") { +"TextReaderRpi" }
                script(src = "/static/app.js") {}
            }
        }
    }
}
