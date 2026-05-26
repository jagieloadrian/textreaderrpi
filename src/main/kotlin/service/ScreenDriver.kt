package com.anjo.service

import com.anjo.driver.DisplayDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ScreenDriverService(private val driver: DisplayDriver,
    private val ioDispatcher: CoroutineDispatcher) {

    suspend fun readInput(input: String) {
        require(input.isNotBlank()) { "Text cannot be blank" }
        withContext(ioDispatcher) {
            driver.scrollText(this, input)
        }
    }
}
