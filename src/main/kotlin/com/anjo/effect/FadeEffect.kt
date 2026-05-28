package com.anjo.effect

import com.anjo.driver.DisplayDriver
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

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

