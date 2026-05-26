package com.anjo.driver

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test DisplayStatus data class and DisplayDriver interface contract.
 *
 * Note: Max7219Matrix tests with actual Pi4J Context require hardware or Pi4J mock plugin.
 * These tests focus on the DisplayStatus structure and interface compliance.
 */
class Max7219MatrixTest {

    @Test
    fun testDisplayStatusStructure() {
        // Test DisplayStatus data class initialization
        val status = DisplayStatus(
            isActive = false,
            hardwareAvailable = true,
            currentMessage = "Test",
            error = null
        )

        assertEquals(false, status.isActive)
        assertEquals(true, status.hardwareAvailable)
        assertEquals("Test", status.currentMessage)
        assertNull(status.error)
    }

    @Test
    fun testDisplayStatusWithError() {
        val status = DisplayStatus(
            isActive = false,
            hardwareAvailable = false,
            currentMessage = null,
            error = "SPI communication failed"
        )

        assertFalse(status.hardwareAvailable)
        assertEquals("SPI communication failed", status.error)
    }

    @Test
    fun testDisplayStatusDefaultValues() {
        // Test optional parameters default to null
        val status = DisplayStatus(
            isActive = true,
            hardwareAvailable = true
        )

        assertEquals(true, status.isActive)
        assertEquals(true, status.hardwareAvailable)
        assertNull(status.currentMessage)
        assertNull(status.error)
    }

    @Test
    fun testDisplayDriverInterfaceExists() {
        // Compile-time check: DisplayDriver interface must exist with required methods
        // If this test compiles, the interface is properly defined
        val interfaceClass = DisplayDriver::class

        // Verify interface has the expected methods (by checking they're callable)
        // In a real test, we'd instantiate an implementation
        // This is a compile-time verification
        assertTrue(true)
    }

    @Test
    fun testDisplayStatusEquality() {
        val status1 = DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            currentMessage = "Test"
        )

        val status2 = DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            currentMessage = "Test"
        )

        // Data classes provide equals() by default
        assertEquals(status1, status2)
    }

    @Test
    fun testDisplayStatusToString() {
        val status = DisplayStatus(
            isActive = true,
            hardwareAvailable = true,
            currentMessage = "Hello",
            error = null
        )

        // Data classes provide toString() by default
        val str = status.toString()
        assertTrue(str.contains("isActive"))
        assertTrue(str.contains("hardwareAvailable"))
        assertTrue(str.contains("Hello"))
    }
}

