package com.anjo.config.model

/**
 * Configuration for all display types.
 * type: "MAX7219" | "LCD" | "OLED"
 */
data class DisplayConfig(
    val type: String = "MAX7219",
    val max7219: Max7219Config = Max7219Config(),
    val lcd: LcdConfig = LcdConfig(),
    val oled: OledConfig = OledConfig()
)

/**
 * MAX7219 LED matrix configuration
 */
data class Max7219Config(
    val numDevices: Int = 2,
    val brightness: Boolean = true,
    val gpioPins: Map<String, Int> = mapOf(
        "spi_ce" to 8,
        "spi_mosi" to 10,
        "spi_miso" to 9,
        "spi_sck" to 11
    )
)

/**
 * I2C LCD (16x2 HD44780) configuration
 */
data class LcdConfig(
    val i2cAddress: Int = 0x27,
    val busNumber: Int = 1,
    val rows: Int = 2,
    val columns: Int = 16
)

/**
 * I2C OLED (SSD1306) configuration
 */
data class OledConfig(
    val i2cAddress: Int = 0x3C,
    val busNumber: Int = 1,
    val width: Int = 128,
    val height: Int = 64
)

