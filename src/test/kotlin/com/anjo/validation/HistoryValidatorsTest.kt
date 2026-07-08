package com.anjo.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.parametersOf

class HistoryValidatorsTest : FunSpec({

    test("sanitizeSearchTerm strips percent sign from input") {
        HistoryValidators.sanitizeSearchTerm("ab%cd") shouldBe "abcd"
    }

    test("sanitizeSearchTerm strips underscore from input") {
        HistoryValidators.sanitizeSearchTerm("ab_cd") shouldBe "abcd"
    }

    test("sanitizeSearchTerm wildcard-only input becomes empty string") {
        HistoryValidators.sanitizeSearchTerm("%_%_") shouldBe ""
    }

    test("sanitizeSearchTerm clean input is unchanged") {
        HistoryValidators.sanitizeSearchTerm("hello") shouldBe "hello"
    }

    test("sanitizeSearchTerm strips multiple mixed wildcards") {
        HistoryValidators.sanitizeSearchTerm("a%b_c%d") shouldBe "abcd"
    }

    test("parseFilter wildcard-only search yields null search filter") {
        HistoryValidators.parseFilter(parametersOf("search", "%_%")).search shouldBe null
    }

    test("parseFilter sanitizes search before the blank check") {
        HistoryValidators.parseFilter(parametersOf("search", "a%b")).search shouldBe "ab"
    }

    test("parseFilter effect ALL in any case yields null effect filter") {
        HistoryValidators.parseFilter(parametersOf("effect", "all")).effect shouldBe null
    }

    test("parseFilter lowercase effect is uppercased") {
        HistoryValidators.parseFilter(parametersOf("effect", "scroll")).effect shouldBe "SCROLL"
    }
})
