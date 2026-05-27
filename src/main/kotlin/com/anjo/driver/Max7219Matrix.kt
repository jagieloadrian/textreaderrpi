package com.anjo.driver

import com.anjo.utils.Font
import com.pi4j.context.Context
import com.pi4j.io.spi.Spi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Max7219Matrix(
    private val ctx: Context,
    private val numDevices: Int = 2,
) : DisplayDriver {

    companion object {
        private const val REG_DISPLAY_TEST = 0x0F
        private const val REG_SHUTDOWN     = 0x0C
        private const val REG_SCAN_LIMIT   = 0x0B
        private const val REG_INTENSITY    = 0x0A
        private const val REG_DECODE_MODE  = 0x09
    }

    private val spi: Spi
    private var job: Job? = null
    private var buffer = Array(numDevices) { ByteArray(8) }
    private var lastMessage: String? = null
    private var lastError: String? = null

    init {
        val config = Spi.newConfigBuilder(ctx)
            .id("max7219")
            .name("MAX7219 SPI")
            .bcm(0)
            .baud(1_000_000)
            .build()

        spi = ctx.create(config)
        try {
            initialize()
        } catch (e: Exception) {
            lastError = "Initialization failed: ${e.message}"
        }
    }

    private fun initialize() {
        sendCommand(REG_DISPLAY_TEST, 0x00)
        sendCommand(REG_SHUTDOWN,     0x01)
        sendCommand(REG_SCAN_LIMIT,   0x07)
        sendCommand(REG_INTENSITY,    0x08)
        sendCommand(REG_DECODE_MODE,  0x00)
        clear()
    }

    override fun clear() {
        try {
            for (row in 1..8) sendCommand(row, 0x00)
            buffer = Array(numDevices) { ByteArray(8) }
            lastMessage = null
            lastError = null
        } catch (e: Exception) {
            lastError = "Clear failed: ${e.message}"
        }
    }

    override fun write(text: String) {
        stop()
        clear()
        lastMessage = text
    }

    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) {
        stop()
        lastMessage = text

        val bitmap = buildBitmap(text)

        job = scope.launch {
            val maxOffset = bitmap.size / 8 - (numDevices * 8)
            var offset = 0

            while (isActive && offset < maxOffset) {
                render(bitmap, offset)
                offset++
                delay(speedMs)
            }
        }
    }

    override fun status(): DisplayStatus {
        return DisplayStatus(
            isActive = job?.isActive ?: false,
            hardwareAvailable = lastError == null,
            currentMessage = lastMessage,
            error = lastError,
        )
    }

    override fun stop() {
        job?.cancel()
    }

    private fun sendCommand(register: Int, data: Int) {
        val packet = ByteArray(numDevices * 2)
        for (i in 0 until numDevices) {
            packet[i * 2]     = register.toByte()
            packet[i * 2 + 1] = data.toByte()
        }
        spi.write(packet)
    }

    private fun render(bitmap: ByteArray, offset: Int) {
        val bitmapWidth = bitmap.size / 8
        val baseOffset = offset * 8

        for (d in 0 until numDevices) {
            val deviceOffset = baseOffset + (d * 8)
            val rowBuf = buffer[d]

            for (row in 0 until 8) {
                var value = 0
                for (col in 0 until 8) {
                    value = (value shl 1) or bitmap.getSafe(deviceOffset + col, row, bitmapWidth)
                }
                rowBuf[row] = value.toByte()
            }
        }

        flush(buffer)
    }

    private fun buildBitmap(text: String): ByteArray {
        val columns = buildList {
            for (c in text) {
                for (col in getChar(c)) add(col.toInt())
                add(0)
            }
        }
        val bitmap = ByteArray(columns.size * 8)
        var i = 0
        for (col in columns) {
            for (bit in 0 until 8) {
                bitmap[i++] = ((col shr bit) and 1).toByte()
            }
        }
        return bitmap
    }

    private fun flush(buf: Array<ByteArray>) {
        val packet = ByteArray(numDevices * 2)
        for (row in 0 until 8) {
            var i = 0
            for (d in 0 until numDevices) {
                packet[i++] = (row + 1).toByte()
                packet[i++] = buf[d][row]
            }
            spi.write(packet)
        }
    }

    private fun getChar(c: Char): ByteArray = Font.asciiFont[c] ?: Font.asciiFont[' ']!!

    private fun ByteArray.getSafe(x: Int, row: Int, width: Int): Int =
        if (x < width) this[(x * 8) + row].toInt() else 0
}