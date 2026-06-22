package com.anjo.web.templates

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.html.div
import kotlinx.html.stream.appendHTML

class HtmlUtilsTest : FunSpec({

    fun render(text: String, term: String?): String =
        buildString { appendHTML().div { highlightText(text, term)() } }

    test("highlightText wraps the matching word in a mark element") {
        val output = render("hello world", "world")
        output shouldContain "<mark>world</mark>"
    }

    test("highlightText highlights all occurrences case-insensitively") {
        val output = render("Hello HELLO hello", "hello")
        output shouldContain "<mark>Hello</mark>"
        output shouldContain "<mark>HELLO</mark>"
        output shouldContain "<mark>hello</mark>"
    }

    test("highlightText with null term emits plain text and no mark element") {
        val output = render("plain text", null)
        output shouldNotContain "<mark>"
        output shouldContain "plain text"
    }

    test("highlightText with empty term emits plain text and no mark element") {
        val output = render("plain text", "")
        output shouldNotContain "<mark>"
        output shouldContain "plain text"
    }

    test("highlightText escapes angle brackets in record text and does not inject raw tags") {
        val output = render("a <b> c", "b")
        output shouldNotContain "<b>"
        output shouldContain "&lt;"
        output shouldContain "&gt;"
    }
})
