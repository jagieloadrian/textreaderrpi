package com.anjo.service


import com.pi4j.context.Context
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiConfig


interface ScreenDriver {
    fun readInput(input: String)
}

class Max7219(
    pi4j: Context,
    bus: Int = 0,
    device: Int = 0,
    private val devices: Int = 1, // ile układów w łańcuchu
) {

    private val spi: Spi

    init {
        val config: SpiConfig = Spi.newConfigBuilder(pi4j)
            .id("max7219")
            .bus(bus)
            .baud(1_000_000)
            .build()

        spi = pi4j.create(config)

        initDisplay()
    }

    private fun initDisplay() {
        writeAll(0x0F, 0x00) // display test off
        writeAll(0x0C, 0x01) // normal mode
        writeAll(0x0B, 0x07) // scan limit (8 rows)
        writeAll(0x09, 0x00) // no decode
        setIntensity(8)
        clear()
    }

    fun setIntensity(value: Int) {
        writeAll(0x0A, value.coerceIn(0, 15))
    }

    fun clear() {
        for (row in 1..8) {
            writeAll(row, 0x00)
        }
    }

    fun setRow(deviceIndex: Int, row: Int, value: Int) {
        val buffer = ByteArray(devices * 2)

        for (i in 0 until devices) {
            val offset = i * 2
            if (i == deviceIndex) {
                buffer[offset] = row.toByte()
                buffer[offset + 1] = value.toByte()
            } else {
                buffer[offset] = 0x00
                buffer[offset + 1] = 0x00
            }
        }

        spi.write(buffer)
    }

    private fun writeAll(register: Int, data: Int) {
        val buffer = ByteArray(devices * 2)

        for (i in 0 until devices) {
            val offset = i * 2
            buffer[offset] = register.toByte()
            buffer[offset + 1] = data.toByte()
        }

        spi.write(buffer)
    }


    fun setPixel(x: Int, y: Int, on: Boolean) {
        val device = x / 8
        val col = x % 8

        val row = y + 1

        val value = if (on) (1 shl col) else 0
        setRow(device, row, value)
    }

    fun shutdown() {
        spi.close()
    }
}