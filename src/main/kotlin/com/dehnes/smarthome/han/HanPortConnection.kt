package com.dehnes.smarthome.han

import com.dehnes.smarthome.utils.CmdExecutor
import com.dehnes.smarthome.utils.SerialPortFinder.findSerialPort
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration

interface IHanPortConnection : Closeable {
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

class HanPortConnectionDev private constructor(
    private val inputStream: InputStream
) : IHanPortConnection {

    companion object {
        private val logger = KotlinLogging.logger { }

        fun open(): HanPortConnectionDev {
            val filename = findSerialPort("Prolific_Technology_Inc._USB-Serial_Controller_ALDNt114J20")
            // set baud
            CmdExecutor.runToString(
                listOf(
                    "stty",
                    "-F",
                    filename,
                    "2400",
                    "raw",
                    "cs8"
                ),
                Duration.ofSeconds(20)
            )

            return HanPortConnectionDev(File(filename).inputStream())
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = inputStream.read(buffer, offset, length)

    override fun close() {
        inputStream.close()
    }
}