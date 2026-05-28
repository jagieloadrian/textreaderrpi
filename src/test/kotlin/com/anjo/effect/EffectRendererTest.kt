package com.anjo.effect

import com.anjo.driver.DisplayDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EffectRendererTest : FunSpec({

    test("ScrollEffect calls scrollText with original text") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            ScrollEffect().render("hello", mockDriver)
            coVerify { mockDriver.scrollText(any(), "hello", any()) }
        }
    }

    test("ReverseEffect calls scrollText with reversed text") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            ReverseEffect().render("hello", mockDriver)
            coVerify { mockDriver.scrollText(any(), "olleh", any()) }
            coVerify(exactly = 0) { mockDriver.scrollText(any(), "hello", any()) }
        }
    }

    test("BlinkEffect calls setBrightness(0) and setBrightness(15) alternately") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            val capturedLevels = mutableListOf<Int>()
            coEvery { mockDriver.setBrightness(capture(capturedLevels)) } returns Unit

            BlinkEffect(blinkCount = 2, blinkIntervalMs = 1L).render("blink", mockDriver)

            coVerify(atLeast = 2) { mockDriver.setBrightness(0) }
            coVerify(atLeast = 3) { mockDriver.setBrightness(15) }

            // Verify setBrightness(0) appears in the captured sequence
            capturedLevels shouldContain 0
            capturedLevels shouldContain 15
        }
    }

    test("BlinkEffect restores brightness to 15 after completion") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            val capturedLevels = mutableListOf<Int>()
            coEvery { mockDriver.setBrightness(capture(capturedLevels)) } returns Unit

            BlinkEffect(blinkCount = 1, blinkIntervalMs = 1L).render("blink", mockDriver)

            // Last call must restore brightness to 15
            capturedLevels.last() shouldBe 15
        }
    }

    test("FadeEffect ramps brightness from 0 to 15") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            val capturedLevels = mutableListOf<Int>()
            coEvery { mockDriver.setBrightness(capture(capturedLevels)) } returns Unit

            FadeEffect(stepMs = 1L).render("fade", mockDriver)

            // First call is setBrightness(0) — initial dark state
            capturedLevels.first() shouldBe 0
            // Contains 15 (max brightness after ramp)
            capturedLevels shouldContain 15
            // The ramp is monotonically increasing from 0 to 15
            val rampPart = capturedLevels.takeLast(16) // last 16 calls are the 0..15 ramp
            rampPart shouldBe (0..15).toList()
        }
    }

    test("FadeEffect calls scrollText after fade-in") {
        runTest {
            val mockDriver = mockk<DisplayDriver>(relaxed = true)
            FadeEffect(stepMs = 1L).render("fade", mockDriver)
            coVerify { mockDriver.scrollText(any(), "fade", any()) }
        }
    }
})

