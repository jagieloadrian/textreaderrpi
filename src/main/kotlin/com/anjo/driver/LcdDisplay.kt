package com.anjo.driver

import com.pi4j.context.Context
import com.pi4j.io.i2c.I2C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LcdDisplay(
    private val ctx: Context,
    private val i2cAddress: Int = 0x27,
    private val busNumber: Int = 1,
) : DisplayDriver {

    private val i2c: I2C?
    private var job: Job? = null
    private var lastMessage: String? = null
    private var lastError: String? = null
    private val maxLineLength = 16
    private val cursorLine1 = 128
    private val cursorLine2 = 192

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

    private fun initializeLcd() {
        writeCommand(0x33)
        writeCommand(0x32)
        writeCommand(0x28)
        writeCommand(0x0C)
        writeCommand(0x06)
        clear()
    }

    private fun writeI2C(byte: Int) {
        i2c?.write(byte.toByte())
    }

    private fun writeCommand(cmd: Int) {
        try {
            writeI2C(cmd or 0x04)
            writeI2C(cmd and 0xFB)
        } catch (e: Exception) {
            lastError = "Write command failed: ${e.message}"
        }
    }

    private fun writeData(data: Int) {
        try {
            writeI2C(data or 0x05)
            writeI2C(data or 0x01)
        } catch (e: Exception) {
            lastError = "Write data failed: ${e.message}"
        }
    }

    private fun setCursorAndWrite(position: Int, char: Char) {
        try {
            writeCommand(0x80 or position)
            writeData(char.code)
        } catch (e: Exception) {
            lastError = "Cursor write failed: ${e.message}"
        }
    }

    override fun clear() {
        try {
            stop()
            writeCommand(0x01)
            writeCommand(0x02)
            lastMessage = null
            lastError = null
        } catch (e: Exception) {
            lastError = "Clear failed: ${e.message}"
        }
    }

    override fun write(text: String) {
        stop()
        lastMessage = text

        try {
            clear()
            val lines = text.chunked(maxLineLength)

            if (lines.isNotEmpty()) {
                writeCommand(cursorLine1)
                lines[0].forEachIndexed { index, char ->
                    if (index < maxLineLength) writeData(char.code)
                }
            }

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

    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) {
        stop()
        lastMessage = text

        job = scope.launch {
            try {
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

    override fun status(): DisplayStatus {
        val isHardwareOk = i2c != null && lastError == null
        return DisplayStatus(
            isActive = job?.isActive ?: false,
            hardwareAvailable = isHardwareOk,
            currentMessage = lastMessage,
            error = lastError
        )
    }

    override fun stop() {
        job?.cancel()
    }
}
