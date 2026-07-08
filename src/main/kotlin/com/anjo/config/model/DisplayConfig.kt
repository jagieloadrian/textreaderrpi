package com.anjo.config.model

import com.anjo.model.DisplayType

data class DisplayConfig(
    val type: DisplayType = DisplayType.MAX7219,
    val max7219: Max7219Config = Max7219Config(),
    val lcd: LcdConfig = LcdConfig(),
    val oled: OledConfig = OledConfig()
)

data class Max7219Config(
    val numDevices: Int = 2,
    val brightness: Boolean = true
)

data class LcdConfig(
    val i2cAddress: Int = 0x27,
    val busNumber: Int = 1
)

data class OledConfig(
    val i2cAddress: Int = 0x3C,
    val busNumber: Int = 1,
    val width: Int = 128,
    val height: Int = 64
)

