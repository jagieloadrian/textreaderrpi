package com.anjo.service.effect

import com.anjo.driver.DisplayDriver
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

interface EffectRenderer {
    suspend fun render(text: String, driver: DisplayDriver)
}

class BlinkEffect(
    private val blinkCount: Int = 3,
    private val blinkIntervalMs: Long = 500L
) : EffectRenderer {
    override suspend fun render(text: String, driver: DisplayDriver) {
        driver.displayStatic(text)
        repeat(blinkCount) {
            delay(blinkIntervalMs)
            driver.setBrightness(0)
            delay(blinkIntervalMs)
            driver.setBrightness(15)
        }
        driver.setBrightness(15)
    }
}

class FadeEffect(private val stepMs: Long = 100L) : EffectRenderer {
    override suspend fun render(text: String, driver: DisplayDriver) {
        driver.displayStatic(text)
        driver.setBrightness(0)
        for (level in 0..15) {
            driver.setBrightness(level)
            delay(stepMs)
        }
        coroutineScope { driver.scrollText(this, text) }
    }
}

class ReverseEffect : EffectRenderer {
    override suspend fun render(text: String, driver: DisplayDriver) {
        coroutineScope { driver.scrollText(this, text.reversed()) }
    }
}

class ScrollEffect : EffectRenderer {
    override suspend fun render(text: String, driver: DisplayDriver) {
        coroutineScope { driver.scrollText(this, text) }
    }
}
