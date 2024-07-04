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

class HanPortConnection private constructor(
    private val socket: Socket
) : IHanPortConnection {

    companion object {
        fun open(host: String, port: Int, connectTimeout: Int = 5000, readTimeout: Int = 60000) = HanPortConnection(
            Socket().apply {
                soTimeout = readTimeout
                connect(InetSocketAddress(host, port), connectTimeout)
            }
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        socket.getInputStream().read(buffer, offset, length)

    override fun close() {
        kotlin.runCatching { socket.close() }
    }
}

interface IHanPortConnection : Closeable {
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

class HanPortConnectionDev private constructor(
    private val inputStream: InputStream
) : IHanPortConnection {

    companion object {
        private val logger = KotlinLogging.logger { }

        fun open(): HanPortConnectionDev {
            val filename = findSerialPort("Prolific_Technology_Inc._USB-Serial_Controller_ALCSt114J20")
            // set baud
            CmdExecutor.runToString(
                listOf(
                    "stty",
                    "-F",
                    filename,
                    "2400",
                    "cs8",
                    "-parenb",
                    "-parodd",
                    "-crtscts",
                    "min",
                    "1",
                    "time",
                    "0",
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