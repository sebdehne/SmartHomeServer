package com.dehnes.smarthome.rf433

import mu.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

/*
 * Send and receive messages over RF 433Mhz (using a TCP 2 serial-port bridge)
 */
class Rf433Client(
    private val executorService: ExecutorService,
    host: String = "localhost",
    port: Int = 23000,
    private val myAddr: Int = 1
) {

    val listeners = CopyOnWriteArrayList<(rfPacket: RfPacket) -> Unit>()
    private val dst = InetSocketAddress(host, port)
    private val logger = KotlinLogging.logger { }

    @Volatile
    private var isStarted = false

    @Volatile
    private var ouputStream: OutputStream? = null

    @PostConstruct
    @Synchronized
    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        Thread {
            while (isStarted) {
                try {
                    readLoop()
                } catch (e: Exception) {
                    logger.error("", e)
                }
                Thread.sleep(30 * 1000)
            }
        }.start()
    }

    @PreDestroy
    @Synchronized
    fun stop() {
        if (!isStarted) {
            return
        }
        isStarted = false
    }

    fun send(rfPacket: RfPacket, retries: Int = 3) {
        repeat(retries) {
            (ouputStream ?: error("Currently not connected")).write(rfPacket.toBytes())
            Thread.sleep(100)
        }
        logger.info { "Sent retries=$retries packet=$rfPacket" }
    }

    private fun readLoop() {
        connect().use { conn ->
            ouputStream = conn.outputStream

            val buf = ByteArray(1024)
            val writePos = AtomicInteger(0)
            while (isStarted) {
                val nextPacket = tryReadNextPacket(conn.inputStream, buf, writePos) ?: break
                listeners.forEach { l ->
                    executorService.submit {
                        try {
                            l(nextPacket)
                        } catch (e: Exception) {
                            logger.error("Error handling packet $nextPacket", e)
                        }
                    }
                }
            }
        }
    }

    private fun tryReadNextPacket(inputStream: InputStream, buf: ByteArray, writePos: AtomicInteger): RfPacket? {
        // packet format: <errorCode>,<dst>,<from>,<msgLen>,msg...
        while (true) {

            // we need at least 4 bytes
            if (writePos.get() >= 4) {
                val errorCode = buf[0].toInt()
                val dst = buf[1].toInt()
                val from = buf[2].toInt()
                val msgLen = buf[3].toInt()
                val debug = ByteArray(4)
                System.arraycopy(buf, 0, debug, 0, 4)
                logger.debug("Got {}", debug)
                if (errorCode != 0) { // skip error
                    logger.debug("Skipping error $errorCode")
                    compact(buf, 1, writePos)
                    continue
                }
                if (writePos.get() >= 4 && msgLen < 1) {
                    logger.debug("Skipping negative msgLen")
                    compact(buf, 4, writePos)
                    continue
                }

                // do we have a complete packet?
                if (writePos.get() >= 4 + msgLen) {
                    val msg = ByteArray(msgLen)
                    System.arraycopy(buf, 4, msg, 0, msgLen)
                    val p = RfPacket(
                        from,
                        convert(msg)
                    )
                    compact(buf, 4 + msgLen, writePos)
                    if (dst == myAddr) {
                        logger.info("Received packet $p")
                        return p
                    } else {
                        logger.debug("Packet not for me $p")
                    }
                }
            }
            try {
                writePos.set(
                    writePos.get() + inputStream.read(
                        buf,
                        writePos.get(),
                        buf.size - writePos.get()
                    )
                )
            } catch (e: IOException) {
                return null
            }
            if (writePos.get() == buf.size) {
                // forced to give up
                logger.info("Buffer is full, giving up")
                return null
            }
        }
    }

    private fun compact(buf: ByteArray, firstByte: Int, writePos: AtomicInteger) {
        System.arraycopy(buf, firstByte, buf, 0, buf.size - firstByte)
        writePos.set(writePos.get() - firstByte)
    }

    private fun connect(): Connection {
        val socket = Socket()
        socket.connect(dst, 10000)
        return Connection(
            socket,
            socket.getInputStream(),
            socket.getOutputStream()
        ).apply {
            logger.info("Connected to $dst")
        }
    }

    private fun convert(input: ByteArray): IntArray {
        return if (input.isNotEmpty()) {
            val a = IntArray(input.size)
            for (i in input.indices) {
                a[i] = input[i].toInt() and 0xFF
            }
            a
        } else {
            IntArray(0)
        }
    }

}

class Connection(
    val socket: Socket,
    val inputStream: InputStream,
    val outputStream: OutputStream
) : Closeable {
    override fun close() {
        try {
            inputStream.close()
        } catch (e: Exception) {
        }
        try {
            outputStream.close()
        } catch (e: Exception) {
        }
        try {
            socket.close()
        } catch (e: Exception) {
        }
    }

}

data class RfPacket(val remoteAddr: Int, val message: IntArray) {
    fun toBytes(): ByteArray {
        val buf = ByteArray(message.size + 1)
        buf[0] = remoteAddr.toByte()
        message.forEachIndexed { index, i ->
            buf[index + 1] = i.toByte()
        }
        return buf
    }
}