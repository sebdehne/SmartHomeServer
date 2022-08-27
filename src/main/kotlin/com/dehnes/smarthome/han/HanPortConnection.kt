package com.dehnes.smarthome.han

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket

class HanPortConnection private constructor(
    private val socket: Socket
) : Closeable {

    companion object {
        fun open(host: String, port: Int, connectTimeout: Int = 5000, readTimeout: Int = 60000) = HanPortConnection(
            Socket().apply {
                soTimeout = readTimeout
                connect(InetSocketAddress(host, port), connectTimeout)
            }
        )
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int = socket.getInputStream().read(buffer, offset, length)

    override fun close() {
        kotlin.runCatching { socket.close() }
    }
}