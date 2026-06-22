package com.anjo.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
})
