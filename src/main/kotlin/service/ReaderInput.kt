package com.anjo.service

class ReaderInputService(private val driver: ScreenDriverService) {
    suspend fun readInput(input: String) {
       driver.readInput(input)
    }

}