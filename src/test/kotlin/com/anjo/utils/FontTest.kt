package com.anjo.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FontTest : FunSpec({

    test("should return mapped glyph for known character") {
        val result = Font.getChar('A')
        result shouldBe byteArrayOf(126, 17, 17, 17, 126)
    }

    test("should return blank ByteArray for unmapped character") {
        val result = Font.getChar('中')
        result shouldBe ByteArray(5) { 0 }
    }

    test("should return space glyph for space character") {
        val result = Font.getChar(' ')
        result shouldBe byteArrayOf(0, 0, 0, 0, 0)
    }
})
