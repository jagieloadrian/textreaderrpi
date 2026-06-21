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
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

private val NAV_ITEMS = listOf(
    Triple("/",         "M2 21l21-9L2 3v7l15 2-15 2v7z",                                            "Send Text"),
    Triple("/schedule", "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z", "Schedules"),
    Triple("/history",  "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z", "History"),
    Triple("/zones",    "M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z", "Zones"),
    Triple("/status",   "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z", "Status"),
)

private const val ICON_COLLAPSE = "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"
private const val ICON_HAMBURGER = "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"

private fun svgIconHtml(path: String, cls: String = "") =
    """<svg class="nav-icon${if (cls.isNotEmpty()) " $cls" else ""}" width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="$path"/></svg>"""

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
                    a(href = "/") { +"TextReaderRpi" }
                    button { id = "navToggle"; unsafe { raw(svgIconHtml(ICON_HAMBURGER)) } }
                }
                aside {
                    div {
                        attributes["class"] = "aside-header"
                        a(href = "/") {
                            attributes["class"] = "aside-title"
                            unsafe { raw(svgIconHtml("M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5")) }
                            span { attributes["class"] = "nav-label"; +"TextReaderRpi" }
                        }
                        button {
                            id = "navCollapseBtn"
                            attributes["title"] = "Collapse menu"
                            unsafe { raw(svgIconHtml(ICON_COLLAPSE, "collapse-icon")) }
                        }
                    }
                    nav {
                        ul {
                            for ((href, iconPath, label) in NAV_ITEMS) {
                                li {
                                    a(href = href) {
                                        if (activePath == href) attributes["aria-current"] = "page"
                                        unsafe { raw(svgIconHtml(iconPath)) }
                                        span { attributes["class"] = "nav-label"; +label }
                                    }
                                }
                            }
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
