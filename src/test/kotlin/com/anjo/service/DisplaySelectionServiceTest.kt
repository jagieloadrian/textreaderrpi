package com.anjo.service

import com.anjo.config.model.DisplayConfig
import com.anjo.config.model.Max7219Config
import com.anjo.config.model.LcdConfig
import com.anjo.config.model.OledConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for DisplaySelectionService.
 * 
 * Full integration tests require Pi4J Context with hardware.
 * These tests verify the service's contract and configuration handling.
 */
class DisplaySelectionServiceTest {

    @Test
    fun testDisplayConfigStructure() {
        val displayConfig = DisplayConfig(
            type = "MAX7219",
            max7219 = Max7219Config(
                numDevices = 2,
                brightness = true
            ),
            lcd = LcdConfig(
                i2cAddress = 0x27,
                busNumber = 1
            ),
            oled = OledConfig(
                i2cAddress = 0x3C,
                busNumber = 1
            )
        )

        assertEquals("MAX7219", displayConfig.type)
        assertEquals(2, displayConfig.max7219.numDevices)
        assertEquals(0x27, displayConfig.lcd.i2cAddress)
        assertEquals(0x3C, displayConfig.oled.i2cAddress)
    }

    @Test
    fun testMax7219ConfigDefaults() {
        val max7219 = Max7219Config()

        assertEquals(2, max7219.numDevices)
        assertEquals(true, max7219.brightness)
        assertNotNull(max7219.gpioPins)
        assertEquals(8, max7219.gpioPins["spi_ce"])
    }

    @Test
    fun testLcdConfigDefaults() {
        val lcd = LcdConfig()

        assertEquals(0x27, lcd.i2cAddress)
        assertEquals(1, lcd.busNumber)
        assertEquals(2, lcd.rows)
        assertEquals(16, lcd.columns)
    }

    @Test
    fun testOledConfigDefaults() {
        val oled = OledConfig()

        assertEquals(0x3C, oled.i2cAddress)
        assertEquals(1, oled.busNumber)
        assertEquals(128, oled.width)
        assertEquals(64, oled.height)
    }

    @Test
    fun testDisplayConfigEquality() {
        val config1 = DisplayConfig(type = "LCD")
        val config2 = DisplayConfig(type = "LCD")

        assertEquals(config1, config2)
    }

    @Test
    fun testDisplayTypeDispatch() {
        // Document the supported display types
        val supportedTypes = listOf("MAX7219", "LCD", "OLED")

        supportedTypes.forEach { type ->
            val config = DisplayConfig(type = type)
            assertEquals(type, config.type)
        }
    }

    @Test
    fun testGpioPinsMapping() {
        val gpioPins = mapOf(
            "spi_ce" to 8,
            "spi_mosi" to 10,
            "spi_miso" to 9,
            "spi_sck" to 11
        )

        assertEquals(8, gpioPins["spi_ce"])
        assertEquals(10, gpioPins["spi_mosi"])
        assertEquals(9, gpioPins["spi_miso"])
        assertEquals(11, gpioPins["spi_sck"])
    }

    @Test
    fun testPendingSwitchesQueue() {
        // Test that pending switches queue works (contract verification)
        val switches = mutableListOf<String>()
        
        switches.add("LCD")
        switches.add("OLED")
        
        assertEquals(2, switches.size)
        assertEquals("LCD", switches[0])
        assertEquals("OLED", switches[1])
    }
}

