package com.anjo.effect

import com.anjo.driver.DisplayDriver
import kotlinx.coroutines.coroutineScope

class ReverseEffect : EffectRenderer {
    override suspend fun render(text: String, driver: DisplayDriver) {
        coroutineScope { driver.scrollText(this, text.reversed()) }
    }
}

