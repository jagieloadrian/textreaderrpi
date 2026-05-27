package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.h2
import kotlinx.html.p

class ErrorPage(
    private val statusCode: Int,
    private val message: String,
) {
    fun render(): String {
        return BaseLayout.render(pageTitle = "Error $statusCode", activePath = "") {
            pageContent()
        }
    }

    private fun FlowContent.pageContent() {
        h2 { +"Error $statusCode" }
        p { +message }
        a(href = "/") { +"Back to home" }
    }
}

