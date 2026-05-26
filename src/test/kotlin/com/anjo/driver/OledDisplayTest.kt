package com.anjo.driver

import com.pi4j.context.Context
import com.pi4j.io.i2c.I2C
import com.pi4j.io.i2c.I2CConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class OledDisplayTest : FunSpec({
    test("implements display contract and exposes healthy status") {
        val context = mockk<Context>()
        val i2c = mockk<I2C>(relaxed = true)
        every { context.create(any<I2CConfig>()) } returns i2c

        val driver = OledDisplay(context)

        driver.clear()
        driver.write("HELLO")
        val status = driver.status()

        status.currentMessage shouldBe "HELLO"
    }

    test("fails fast when i2c is unavailable") {
        val context = mockk<Context>()
        every { context.create(any<I2CConfig>()) } throws IllegalStateException("no bus")

        val driver = OledDisplay(context)
        val status = driver.status()

        status.hardwareAvailable.shouldBeFalse()
        status.error.shouldNotBeNull().shouldContain("I2C initialization failed")
    }

    test("scrollText keeps last message and can be stopped") {
        val context = mockk<Context>()
        val i2c = mockk<I2C>(relaxed = true)
        every { context.create(any<I2CConfig>()) } returns i2c

        val driver = OledDisplay(context)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        driver.scrollText(scope, "SCROLL", 1)
        driver.stop()

        val status = driver.status()
        status.isActive.shouldBeFalse()
    }
})


