package com.anjo.driver

import com.pi4j.context.Context
import com.pi4j.io.i2c.I2C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * LcdDisplay: I2C-based character LCD driver (16x2 HD44780)
 *
 * Implements DisplayDriver interface for I2C LCD displays.
 * Uses Pi4J I2C communication with standard HD44780 protocol.
 *
 * Features:
 * - clear(): Clear display and reset cursor
 * - write(text): Display static text
 * - scrollText(scope, text): Smooth text scrolling animation
 * - status(): Report hardware state
 * - Automatic fail-fast on hardware unavailable
 */
class LcdDisplay(
    private val ctx: Context,
    private val i2cAddress: Int = 0x27, // Default I2C address for LCD
    private val busNumber: Int = 1, // Raspberry Pi I2C bus
) : DisplayDriver {

    private val i2c: I2C?
    private var job: Job? = null
    private var lastMessage: String? = null
    private var lastError: String? = null
    private val maxLineLength = 16 // 16x2 LCD
    private val cursorLine1 = 128 // DDRAM address for line 1
    private val cursorLine2 = 192 // DDRAM address for line 2

    init {
        i2c = try {
            val config = I2C.newConfigBuilder(ctx)
                .id("i2c-lcd")
                .name("I2C LCD Display")
                .bus(busNumber)
                .device(i2cAddress)
                .build()
            ctx.create(config)
        } catch (e: Exception) {
            lastError = "I2C initialization failed: ${e.message}"
            null
        }

        if (i2c != null) {
            try {
                initializeLcd()
            } catch (e: Exception) {
                lastError = "LCD initialization failed: ${e.message}"
            }
        }
    }

    /**
     * Initialize LCD: Set mode, clear display, turn on display + cursor
     */
    private fun initializeLcd() {
        // Initialization sequence for HD44780 (typical)
        writeCommand(0x33) // 8-bit mode
        writeCommand(0x32) // Switch to 4-bit mode
        writeCommand(0x28) // 2 lines, 5×8 font
        writeCommand(0x0C) // Display ON, Cursor OFF
        writeCommand(0x06) // Cursor increment direction
        clear()
    }

    /**
     * Write byte to LCD via I2C with backlight control
     */
    private fun writeI2C(byte: Int) {
        i2c?.write(byte.toByte())
    }

    /**
     * Write command to LCD (RS=0, E pulse)
     */
    private fun writeCommand(cmd: Int) {
        try {
            // Enable bit (bit 2), RS=0 (command mode)
            writeI2C(cmd or 0x04)
            writeI2C(cmd and 0xFB)
        } catch (e: Exception) {
            lastError = "Write command failed: ${e.message}"
        }
    }

    /**
     * Write data byte to LCD (RS=1, E pulse)
     */
    private fun writeData(data: Int) {
        try {
            // Enable bit (bit 2), RS=1 (data mode)
            writeI2C(data or 0x05)
            writeI2C(data or 0x01)
        } catch (e: Exception) {
            lastError = "Write data failed: ${e.message}"
        }
    }

    /**
     * Move cursor to position and write character
     */
    private fun setCursorAndWrite(position: Int, char: Char) {
        try {
            writeCommand(0x80 or position) // Set cursor position
            writeData(char.code)
        } catch (e: Exception) {
            lastError = "Cursor write failed: ${e.message}"
        }
    }

    /**
     * Clear display and reset cursor
     */
    override fun clear() {
        try {
            stop() // Stop any scrolling
            writeCommand(0x01) // Clear display
            writeCommand(0x02) // Return cursor to home
            lastMessage = null
            lastError = null
        } catch (e: Exception) {
            lastError = "Clear failed: ${e.message}"
        }
    }

    /**
     * Display static text on LCD
     * Shows first 16 chars on line 1, next 16 chars on line 2 (if text is longer)
     */
    override fun write(text: String) {
        stop() // Stop any scrolling
        lastMessage = text

        try {
            clear()
            val lines = text.chunked(maxLineLength)

            // Write first line
            if (lines.isNotEmpty()) {
                writeCommand(cursorLine1)
                lines[0].forEachIndexed { index, char ->
                    if (index < maxLineLength) writeData(char.code)
                }
            }

            // Write second line if text overflows
            if (lines.size > 1) {
                writeCommand(cursorLine2)
                lines[1].forEachIndexed { index, char ->
                    if (index < maxLineLength) writeData(char.code)
                }
            }
        } catch (e: Exception) {
            lastError = "Write failed: ${e.message}"
        }
    }

    /**
     * Scroll text across display
     * Creates left-to-right scrolling motion
     */
    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) {
        stop()
        lastMessage = text

        job = scope.launch {
            try {
                val clearLine = " ".repeat(maxLineLength)
                val paddedText = " ".repeat(maxLineLength) + text + " ".repeat(maxLineLength)

                var offset = 0
                while (isActive && offset < paddedText.length - maxLineLength) {
                    val visible = paddedText.substring(offset, offset + maxLineLength)
                    
                    writeCommand(cursorLine1)
                    visible.forEachIndexed { index, char ->
                        if (index < maxLineLength) writeData(char.code)
                    }

                    offset++
                    delay(speedMs)
                }

                clear()
            } catch (e: Exception) {
                lastError = "Scroll failed: ${e.message}"
            }
        }
    }

    /**
     * Return hardware status
     */
    override fun status(): DisplayStatus {
        val isHardwareOk = i2c != null && lastError == null
        return DisplayStatus(
            isActive = job?.isActive ?: false,
            hardwareAvailable = isHardwareOk,
            currentMessage = lastMessage,
            error = lastError
        )
    }

    /**
     * Stop any running scrolling animation
     */
    override fun stop() {
        job?.cancel()
    }
}

