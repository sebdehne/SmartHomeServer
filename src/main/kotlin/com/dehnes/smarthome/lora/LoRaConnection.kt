package com.dehnes.smarthome.lora

import com.dehnes.smarthome.utils.PersistenceService
import mu.KotlinLogging
import java.io.*
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

class LoRaConnection(
    private val persistenceService: PersistenceService,
    private val executorService: ExecutorService
) {

    private val readTimeInSeconds = 10L

    private val logger = KotlinLogging.logger { }

    val listeners = CopyOnWriteArrayList<(line: String) -> Unit>()

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

    private fun connect(): Connection {

        val serialDev = persistenceService["lora.serial.port", "/dev/cu.usbmodem14401"]!!

        // set baud
        val process = ProcessBuilder("stty -F $serialDev 115200 raw -echo".split(" ")).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroy()
            error("stty did not complete in time")
        }
        check(process.exitValue() == 0) { "Could not set baud rate" }

        val file = File(serialDev)

        return Connection(
            file.inputStream(),
            file.outputStream()
        ).apply {
            logger.info("Connected to LoRa serial port")
        }
    }

    private fun sinkText(conn: Connection, maxItems: Int, timeout: Duration = Duration.ofSeconds(readTimeInSeconds)) {
        repeat(maxItems) {
            if (conn.read(timeout)) {
                logger.info { "Sinked text: ${conn.getText()}" }
            } else {
                return
            }
        }
    }

    private fun readLoop() {
        connect().use { conn ->
            ouputStream = conn.outputStream

            // consume "ready" and "RN2483....."
            sinkText(conn, 2)

            while (cmd(conn, "mac pause", "\\d+".toRegex()) == null) {
                Thread.sleep(1000)
            }

            while (isStarted) {
                if (cmd(conn, "radio rx 0", "ok".toRegex(), false) == null) {
                    Thread.sleep(1000)
                    continue
                }

                if (!conn.read(Duration.ofMinutes(10))) {
                    logger.info { "Nothing received for 10 min?" }
                    continue
                }

                val line = conn.getText()
                if (line == "radio_err") {
                    logger.debug { "Ignoring radio_err" }
                    continue
                }

                val rssi = cmd(conn, "radio get rssi", ".*".toRegex())
                logger.info { "Radio received: $line (rssi=$rssi)" }

//                listeners.forEach { l ->
//                    executorService.submit {
//                        try {
//                            l(line)
//                        } catch (e: Exception) {
//                            logger.error("Error handling LoRa response $line", e)
//                        }
//                    }
//                }
            }
        }
    }

    private fun cmd(connection: Connection, cmd: String, match: Regex, logSuccess: Boolean = true): String? {
        connection.outputStream.write("$cmd\r\n".toByteArray())

        val response = if (connection.read(Duration.ofSeconds(readTimeInSeconds))) {
            connection.getText()
        } else {
            error("No response received while waiting for $cmd")
        }

        return if (response.matches(match)) {
            if (logSuccess) {
                logger.info { "success: $cmd: $response" }
            }
            response
        } else {
            logger.warn { "Command failed: $cmd, got: $response" }
            sinkText(connection, Int.MAX_VALUE, Duration.ofSeconds(2))
            null
        }
    }

}

class Connection(
    val inputStream: InputStream,
    val outputStream: OutputStream
) : Closeable {

    private val byteBuffer = ByteBuffer.allocate(1024)

    fun read(timeout: Duration): Boolean {
        val deadLineNanos = System.nanoTime() + timeout.toNanos()

        while (System.nanoTime() < deadLineNanos) {
            if (inputStream.available() < 1) {
                Thread.sleep(100) // lazy man non-blocking mode ;)
                continue
            }
            val i = inputStream.read()
            if (i < 0) {
                break
            }
            val c = i.toChar()
            if (c == '\r') {
                continue
            }
            if (c == '\n') {
                byteBuffer.flip()
                return true
            }
            byteBuffer.put(i.toByte())
        }
        return false
    }

    fun getText(): String {
        val arr = ByteArray(byteBuffer.remaining())
        byteBuffer.get(arr)
        byteBuffer.compact()
        return String(arr)
    }

    override fun close() {
        try {
            inputStream.close()
        } catch (e: Exception) {
        }
        try {
            outputStream.close()
        } catch (e: Exception) {
        }
    }
}