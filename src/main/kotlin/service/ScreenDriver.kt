package com.anjo.service

import com.anjo.driver.Max7219Matrix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ScreenDriverService(private val driver: Max7219Matrix,
    private val ioDispatcher: CoroutineDispatcher) {

    suspend fun readInput(input: String) {
        input.validate()
        withContext(ioDispatcher) {
            driver.scrollText(this, input)
        }
    }

    private fun String.validate() {
        TODO("Not yet implemented")
    }
}
