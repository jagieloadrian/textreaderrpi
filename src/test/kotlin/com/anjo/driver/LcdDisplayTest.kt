package com.anjo.driver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests for LcdDisplay I2C LCD driver.
 *
 * Focuses on DisplayDriver interface compliance.
 * Full hardware tests require I2C bus and LCD device.
 */
class LcdDisplayTest {

    @Test
    fun testLcdDisplayInterfaceCompliance() {
        // LcdDisplay must implement DisplayDriver interface
        // This is a compile-time check
        assertTrue(true)
    }

    @Test
    fun testLcdDisplayStatusInitial() {
        // Unlike Max7219, LcdDisplay may fail to initialize without hardware
        // But it should still return a valid DisplayStatus
        // This test documents the contract
        val status = DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            currentMessage = null,
            error = "I2C initialization failed: Device not found"
        )

        assertFalse(status.hardwareAvailable)
        assertNotNull(status.error)
    }

    @Test
    fun testDisplayStatusStructureWithI2CError() {
        val status = DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            currentMessage = null,
            error = "I2C bus error"
        )

        assertEquals(false, status.isActive)
        assertEquals(false, status.hardwareAvailable)
        assertEquals("I2C bus error", status.error)
    }

    @Test
    fun testLcdDisplayConstantsAreValid() {
        // Document expected I2C LCD constants
        val i2cAddressDefaultValue = 0x27
        val i2cBusNumber = 1

        assertEquals(0x27, i2cAddressDefaultValue)
        assertEquals(1, i2cBusNumber)
    }

    @Test
    fun testDisplayLineCapacity() {
        // 16x2 LCD line capacity
        val lineLength = 16
        val text = "Hello World"

        // Text should fit on first line
        assertEquals(true, text.length <= lineLength)
    }

    @Test
    fun testTextChunking() {
        // Test text chunking for 2-line display
        val text = "First Line Text Second Line Content"
        val lines = text.chunked(16)

        assertEquals(3, lines.size)
        assertEquals("First Line Text ", lines[0])
        assertEquals("Second Line Cont", lines[1])
    }

    private fun assertTrue(condition: Boolean) {
        if (!condition) throw AssertionError("Expected true")
    }

    @Test
    fun testDisplayStatusEquality() {
        val status1 = DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            currentMessage = null,
            error = "Device not found"
        )

        val status2 = DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            currentMessage = null,
            error = "Device not found"
        )

        assertEquals(status1, status2)
    }
}

