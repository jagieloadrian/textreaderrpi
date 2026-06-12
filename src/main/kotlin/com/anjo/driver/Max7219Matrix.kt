package com.anjo.driver

import com.anjo.utils.Font
import com.pi4j.context.Context
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import com.pi4j.io.spi.SpiMode
import com.pi4j.plugin.linuxfs.provider.spi.LinuxFsSpiProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class Max7219Matrix(
    private val ctx: Context,
    private val numDevices: Int = 2,
) : AbstractDisplayDriver() {

    companion object {
        private const val REG_DISPLAY_TEST = 0x0F
        private const val REG_SHUTDOWN     = 0x0C
        private const val REG_SCAN_LIMIT   = 0x0B
        private const val REG_INTENSITY    = 0x0A
        private const val REG_DECODE_MODE  = 0x09

        internal fun buildPacket(bitmap: ByteArray, offset: Int, numDevices: Int, row: Int): ByteArray {
            val packet = ByteArray(numDevices * 2)
            for (d in 0 until numDevices) {
                val physicalD = numDevices - 1 - d
                var columnByte = 0
                for (col in 0 until 8) {
                    val globalCol = offset + (physicalD * 8) + col
                    val bit = if (globalCol < bitmap.size) {
                        bitmap[globalCol].toInt() and (1 shl row) != 0
                    } else false
                    columnByte = (columnByte shl 1) or (if (bit) 1 else 0)
                }
                packet[d * 2]     = (row + 1).toByte()
                packet[d * 2 + 1] = columnByte.toByte()
            }
            return packet
        }
    }

    private val spi: Spi?
    private var buffer = Array(numDevices) { ByteArray(8) }

    init {
        spi = try {
            val config = Spi.newConfigBuilder(ctx)
                .id("max7219")
                .name("MAX7219 SPI")
                .bus(SpiBus.BUS_0)
                .chipSelect(SpiChipSelect.CS_0)
                .baud(1_000_000)
                .mode(SpiMode.MODE_0)
                .provider(LinuxFsSpiProviderImpl::class.java)
                .build()
            ctx.create(config)
        } catch (e: Exception) {
            lastError = "SPI initialization failed: ${e.message}"
            null
        }

        if (spi != null) {
            try {
                initialize()
            } catch (e: Exception) {
                lastError = "Initialization failed: ${e.message}"
            }
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
        stop()                          // cancel scroll job first
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
        val bitmap = buildBitmap(text)
        render(bitmap, 0)
    }

    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) {
        stop()
        lastMessage = text

        val bitmap = buildBitmap(text)
        val visibleColumns = numDevices * 8
        val maxOffset = bitmap.size - visibleColumns

        job = scope.launch {
            var offset = 0
            while (isActive && offset <= maxOffset) {
                render(bitmap, offset)
                offset++
                delay(speedMs.milliseconds)
            }
            clear()
        }
    }

    override fun isHardwareAvailable() = spi != null && lastError == null

    override suspend fun setBrightness(level: Int) {
        sendCommand(REG_INTENSITY, level.coerceIn(0, 15))
    }

    override suspend fun displayStatic(text: String) {
        stop()
        lastMessage = text
        val bitmap = buildBitmap(text)
        render(bitmap, 0)
    }

    private fun sendCommand(register: Int, data: Int) {
        val packet = ByteArray(numDevices * 2)
        for (i in 0 until numDevices) {
            packet[i * 2]     = register.toByte()
            packet[i * 2 + 1] = data.toByte()
        }
        spi?.write(packet)
    }

    private fun render(bitmap: ByteArray, offset: Int) {
        for (row in 0 until 8) {
            spi?.write(buildPacket(bitmap, offset, numDevices, row))
        }
    }

    private fun buildBitmap(text: String): ByteArray {
        val columns = mutableListOf<Byte>()

        for (c in text) {
            val glyph = Font.asciiFont[c] ?: Font.asciiFont[' ']!!
            columns.addAll(glyph.toList())
            columns.add(0)
        }

        repeat(16) { columns.add(0) }

        return columns.toByteArray()
    }

}
