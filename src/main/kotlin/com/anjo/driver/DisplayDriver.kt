package com.anjo.driver

import kotlinx.coroutines.CoroutineScope

interface DisplayDriver {
    fun scrollText(scope: CoroutineScope, text: String, speedMs: Long = 16)
    fun stop()
}

