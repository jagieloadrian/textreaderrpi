package com.anjo.effect

import com.anjo.driver.DisplayDriver

interface EffectRenderer {
    suspend fun render(text: String, driver: DisplayDriver)
}

