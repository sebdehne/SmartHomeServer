package com.dehnes.smarthome.mqtt

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket

data class MqttMessage(
    val id: String
)

class MqttClient private constructor(
    private val socket: Socket,
    readBufferSize: Int,
) : Closeable {

    val readBuffer = ByteArray(readBufferSize)
    var writePos = 0

    companion object {
        fun open(host: String, port: Int, connectTimeout: Int, readtimeout: Int, readBufferSize: Int = 1024 * 1000) =
            MqttClient(
                Socket().apply {
                    soTimeout = readtimeout
                    connect(InetSocketAddress(host, port), connectTimeout)
                },
                readBufferSize
            )
    }

    fun readNextMessage(): MqttMessage {
        val read = socket.getInputStream().read(readBuffer, writePos, readBuffer.size - writePos)
        if (read > -1) {

        }
        TODO()
    }

    override fun close() {
        kotlin.runCatching { socket.close() }
    }


}
