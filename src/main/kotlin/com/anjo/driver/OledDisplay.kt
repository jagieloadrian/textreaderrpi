package com.anjo.driver

import com.pi4j.context.Context
import com.pi4j.io.i2c.I2C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OledDisplay(
    private val ctx: Context,
    private val i2cAddress: Int = 0x3C,
    private val busNumber: Int = 1,
    private val width: Int = 128,
    private val height: Int = 64,
) : DisplayDriver {

    private val i2c: I2C?
    private var job: Job? = null
    private var lastMessage: String? = null
    private var lastError: String? = null

    init {
        i2c = try {
            val config = I2C.newConfigBuilder(ctx)
                .id("i2c-oled")
                .name("I2C OLED Display")
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
                initializeDisplay()
            } catch (e: Exception) {
                lastError = "OLED initialization failed: ${e.message}"
            }
        }
    }

    private fun initializeDisplay() {
        sendCommand(0xAE)
        sendCommand(0xA8)
        sendCommand(height - 1)
        sendCommand(0xAF)
        clear()
    }

    private fun sendCommand(command: Int) {
        i2c?.writeRegister(0x00, command.toByte())
    }

    private fun sendData(data: Int) {
        i2c?.writeRegister(0x40, data.toByte())
    }

    override fun clear() {
        try {
            stop()
            repeat(height / 8) { page ->
                sendCommand(0xB0 + page)
                sendCommand(0x00)
                sendCommand(0x10)
                repeat(width) {
                    sendData(0x00)
                }
            }
            lastMessage = null
            lastError = null
        } catch (e: Exception) {
            lastError = "Clear failed: ${e.message}"
        }
    }

    override fun write(text: String) {
        stop()

        try {
            clear()
            lastMessage = text
            sendCommand(0xB0)
            sendCommand(0x00)
            sendCommand(0x10)

            text.take(width / 8).forEach { char ->
                sendData(char.code and 0xFF)
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
                val padded = " ".repeat(16) + text + " ".repeat(16)
                var index = 0
                while (isActive && index <= padded.length - 16) {
                    write(padded.substring(index, index + 16))
                    index++
                    delay(speedMs)
                }
                clear()
                lastMessage = text
            } catch (e: Exception) {
                lastError = "Scroll failed: ${e.message}"
            }
        }
    }

    override fun status(): DisplayStatus {
        return DisplayStatus(
            isActive = job?.isActive ?: false,
            hardwareAvailable = i2c != null && lastError == null,
            currentMessage = lastMessage,
            error = lastError
        )
    }

    override fun stop() {
        job?.cancel()
    }
}

