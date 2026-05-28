package com.anjo.service.effect

import com.anjo.driver.DisplayDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EffectRendererTest : FunSpec({

    test("should call scrollText with original text for ScrollEffect") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            ScrollEffect().render("hello", mockDriver)
            coVerify { mockDriver.scrollText(any(), "hello", any()) }
        }
    }

    test("should call scrollText with reversed text for ReverseEffect") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            ReverseEffect().render("hello", mockDriver)
            coVerify { mockDriver.scrollText(any(), "olleh", any()) }
            coVerify(exactly = 0) { mockDriver.scrollText(any(), "hello", any()) }
        }
    }

    test("should alternate setBrightness 0 and 15 for BlinkEffect") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            val capturedLevels = mutableListOf<Int>()
            coEvery { mockDriver.setBrightness(capture(capturedLevels)) } returns Unit

            BlinkEffect(blinkCount = 2, blinkIntervalMs = 1L).render("blink", mockDriver)

            coVerify(atLeast = 2) { mockDriver.setBrightness(0) }
            coVerify(atLeast = 3) { mockDriver.setBrightness(15) }
            capturedLevels shouldContain 0
            capturedLevels shouldContain 15
        }
    }

    test("should restore brightness to 15 after BlinkEffect completes") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            val capturedLevels = mutableListOf<Int>()
            coEvery { mockDriver.setBrightness(capture(capturedLevels)) } returns Unit

            BlinkEffect(blinkCount = 1, blinkIntervalMs = 1L).render("blink", mockDriver)

            capturedLevels.last() shouldBe 15
        }
    }

    test("should ramp brightness from 0 to 15 for FadeEffect") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            val capturedLevels = mutableListOf<Int>()
            coEvery { mockDriver.setBrightness(capture(capturedLevels)) } returns Unit

            FadeEffect(stepMs = 1L).render("fade", mockDriver)

            capturedLevels.first() shouldBe 0
            capturedLevels shouldContain 15
            val rampPart = capturedLevels.takeLast(16)
            rampPart shouldBe (0..15).toList()
        }
    }

    test("should call scrollText after fade-in for FadeEffect") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            FadeEffect(stepMs = 1L).render("fade", mockDriver)
            coVerify { mockDriver.scrollText(any(), "fade", any()) }
        }
    }
})

