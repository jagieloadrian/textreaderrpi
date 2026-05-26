package com.anjo.config

data class DisplayConfig(
    val gpioPins: Map<String, Int>,
    val numDevices: Int,
    val brightness: Boolean
)

