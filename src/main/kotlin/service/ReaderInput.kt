package com.anjo.service

class ReaderInputService(private val driver: ScreenDriver) {
    fun readInput(input: String) {
       driver.readInput(input)
    }

}