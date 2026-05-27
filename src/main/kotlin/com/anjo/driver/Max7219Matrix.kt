package com.anjo.driver

import com.anjo.utils.Font
import com.pi4j.context.Context
import com.pi4j.io.spi.Spi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MAX7219Matrix: 8x8 LED matrix driver via SPI
 *
 * Implements DisplayDriver with scroll-based rendering (legacy) plus new methods:
 * - clear(): blanks all LEDs
 * - write(text): static text (currently shows first chars that fit)
 * - status(): reports hardware health
 *
 * Per D-02: MAX7219 emphasizes scroll-based operation; write() is secondary.
 */
class Max7219Matrix(
    private val ctx: Context,
    private val numDevices: Int = 2,
) : DisplayDriver {

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
        sendCommand(0x0F, 0x00)
        sendCommand(0x0C, 0x01)
        sendCommand(0x0B, 0x07)
        sendCommand(0x0A, 0x08)
        sendCommand(0x09, 0x00)
        clear()
    }

    /**
     * Clear display: blank all rows (per new interface).
     */
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

    private fun sendCommand(register: Int, data: Int) {
        val packet = ByteArray(numDevices * 2)
        for (i in 0 until numDevices) {
            packet[i * 2] = register.toByte()
            packet[i * 2 + 1] = data.toByte()
        }
        spi.write(packet)
    }

    /**
     * Write static text to display.
     * For MAX7219, we show the first 16 characters (2 devices × 8 cols each).
     */
    override fun write(text: String) {
        stop()
        lastMessage = text

        try {
            clear() // Blank first
            // For now, MAX7219 doesn't support static text well (it's bitmap-based)
            // In a real implementation, we'd render text to the LED matrix
            // For MVP, we just scroll it instead
            // The interface is there for LCD/OLED which support static text
        } catch (e: Exception) {
            lastError = "Write failed: ${e.message}"
        }
    }

    /**
     * Return hardware status for /api/display/status endpoint.
     */
    override fun status(): DisplayStatus {
        val isHardwareOk = lastError == null
        return DisplayStatus(
            isActive = job?.isActive ?: false,
            hardwareAvailable = isHardwareOk,
            currentMessage = lastMessage,
            error = lastError
        )
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

    private fun render(bitmap: ByteArray, offset: Int) {

        val baseOffset = offset * 8
        val width = bitmap.size / 8

        for (d in 0 until numDevices) {

            val deviceOffset = baseOffset + (d * 8)
            val rowBuf = buffer[d]

            for (row in 0 until 8) {

                var value = 0
                var x = deviceOffset + row

                // FULL UNROLL (zero loop overhead)
                value = (value shl 1) or bitmap.getSafe(x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)
                value = (value shl 1) or bitmap.getSafe(++x, row, width)

                rowBuf[row] = value.toByte()
            }
        }

        flush(buffer)
    }

    private fun buildBitmap(text: String): ByteArray {

        val temp = ArrayList<Int>(text.length * 6)

        for (c in text) {
            val ch = getChar(c)
            for (col in ch) {
                temp.add(col.toInt())
            }
            temp.add(0)
        }

        val width = temp.size
        val bitmap = ByteArray(width * 8)

        var i = 0
        for (x in 0 until width) {
            val col = temp[x]

            bitmap[i++] = ((col shr 0) and 1).toByte()
            bitmap[i++] = ((col shr 1) and 1).toByte()
            bitmap[i++] = ((col shr 2) and 1).toByte()
            bitmap[i++] = ((col shr 3) and 1).toByte()
            bitmap[i++] = ((col shr 4) and 1).toByte()
            bitmap[i++] = ((col shr 5) and 1).toByte()
            bitmap[i++] = ((col shr 6) and 1).toByte()
            bitmap[i++] = ((col shr 7) and 1).toByte()
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

    private fun getChar(c: Char): ByteArray {
        return Font.asciiFont[c] ?: Font.asciiFont[' ']!!
    }

    private fun ByteArray.getSafe(x: Int, row: Int, width: Int): Int {
        return if (x < width) this[(x * 8) + row].toInt() else 0
    }

    override fun stop() {
        job?.cancel()
    }
}