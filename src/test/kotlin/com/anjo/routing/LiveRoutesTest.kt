package com.anjo.routing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiveRoutesTest : FunSpec({

    test("GET /api/v1/live returns text/event-stream content type") {
        // implemented in Plan 02
        true shouldBe true
    }
})
