package com.anjo.effect

import com.anjo.driver.DisplayDriver
import kotlinx.coroutines.delay

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

