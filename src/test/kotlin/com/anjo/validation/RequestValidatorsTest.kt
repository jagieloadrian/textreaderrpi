package com.anjo.validation

import com.anjo.config.model.ApiConfig
import com.anjo.model.TextRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.plugins.requestvalidation.ValidationResult

class RequestValidatorsTest : FunSpec({

    val apiConfig = ApiConfig(maxTextLength = 100, rateLimitPerMinute = 60)

    test("validateTextRequest with speed=-1 returns Invalid with positive integer message") {
        val req = TextRequest(text = "hello", speed = -1)
        val result = RequestValidators.validateTextRequest(req, apiConfig)
        (result as ValidationResult.Invalid).reasons.first() shouldContain "speed"
    }

    test("validateTextRequest with blinkPeriod=0 returns Invalid") {
        val req = TextRequest(text = "hello", blinkPeriod = 0)
        val result = RequestValidators.validateTextRequest(req, apiConfig)
        (result as ValidationResult.Invalid).reasons.first() shouldContain "blinkPeriod"
    }

    test("validateTextRequest with fadeSteps=-5 returns Invalid") {
        val req = TextRequest(text = "hello", fadeSteps = -5)
        val result = RequestValidators.validateTextRequest(req, apiConfig)
        (result as ValidationResult.Invalid).reasons.first() shouldContain "fadeSteps"
    }

    test("validateTextRequest with all timing fields null returns Valid") {
        val req = TextRequest(text = "hello")
        val result = RequestValidators.validateTextRequest(req, apiConfig)
        (result is ValidationResult.Valid) shouldBe true
    }

    test("validateTextRequest with positive timing values returns Valid") {
        val req = TextRequest(text = "hello", speed = 50, blinkPeriod = 500, fadeSteps = 8)
        val result = RequestValidators.validateTextRequest(req, apiConfig)
        (result is ValidationResult.Valid) shouldBe true
    }
})
