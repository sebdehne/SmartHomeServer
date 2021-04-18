package com.dehnes.smarthome.lora

import com.dehnes.smarthome.ev_charging.readInt32Bits
import com.dehnes.smarthome.ev_charging.toHexString
import com.dehnes.smarthome.utils.HexUtils
import com.dehnes.smarthome.utils.PersistenceService
import mu.KotlinLogging
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
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

    val listeners = CopyOnWriteArrayList<(LoRaInboundPacket) -> Boolean>()

    @Volatile
    private var isStarted = false

    private val outQueue: Queue<LoRaOutboundPacketRequest> = ConcurrentLinkedQueue()

    @Volatile
    var interrupted: Boolean = false

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
                Thread.sleep(10 * 1000)
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

    fun send(toAddr: Int, type: LoRaPacketType, payload: ByteArray, onResult: (Boolean) -> Unit) {
        outQueue.add(
            LoRaOutboundPacketRequest(
                toAddr,
                type,
                payload,
                onResult
            )
        )
        interrupted = true
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

    private fun isInterrupted(): Boolean {
        val interrupted = this.interrupted
        return if (interrupted) {
            this.interrupted = false
            true
        } else
            false
    }

    private fun sinkText(conn: Connection, maxItems: Int, timeout: Duration = Duration.ofSeconds(readTimeInSeconds)) {
        repeat(maxItems) {
            conn.read(timeout) { false }
            if (conn.hasCompleteText) {
                logger.info { "Sinked text: ${conn.getText()}" }
            } else {
                return
            }
        }
    }

    private fun readLoop() {
        connect().use { conn ->

            // reset RN2483
            conn.outputStream.write("!".toByteArray())
            // consume "RN2483....."
            sinkText(conn, 1)

            while (cmd(conn, "mac pause", listOf("\\d+".toRegex())) == null) {
                Thread.sleep(1000)
            }

            while (isStarted) {

                if (conn.hasCompleteText) {
                    val line = conn.getText()
                    if (line == "radio_err") {
                        logger.debug { "Ignoring radio_err" }
                    } else {
                        logger.info { "Received: '$line' (${line.toByteArray().contentToString()})" }
                        val packet = LoRaInboundPacket(
                            cmd(conn, "radio get rssi", listOf(".*".toRegex()))?.toInt()
                                ?: error("could not read rssi"),
                            if (line.startsWith("radio_rx")) {
                                HexUtils.decodeHexString(line.replace("radio_rx\\s+".toRegex(), ""))
                            } else {
                                error("Unexpected response=$line")
                            },
                            line
                        )

                        if (packet.getToAddr() != localAddr) {
                            logger.info { "Ignoring packet not for me. $packet" }
                        } else {
                            executorService.submit {
                                try {
                                    val wasAccepted = listeners.any { listener ->
                                        listener(packet)
                                    }
                                    if (!wasAccepted) {
                                        logger.warn { "No listener handled message $packet" }
                                    }
                                } catch (e: Exception) {
                                    logger.error("Error handling LoRa response $line", e)
                                }
                            }
                        }
                    }
                }

                if (conn.isCurrentlyReading()) {
                    conn.read(Duration.ofSeconds(readTimeInSeconds)) { false }
                    if (!conn.hasCompleteText) {
                        error("Timeout while reading - closing connection")
                    }
                    continue
                }

                while (outQueue.peek() != null) {
                    val nextOutPacket = outQueue.poll()

                    val result = cmd(
                        conn, "radio tx " + nextOutPacket.toHex(),
                        listOf("ok".toRegex(), "invalid_param".toRegex(), "busy".toRegex()),
                        listOf("radio_tx_ok".toRegex(), "radio_err".toRegex())
                    ) != null
                    executorService.submit {
                        nextOutPacket.onResult(result)
                    }
                }

                // switch to receive mode
                if (cmd(
                        conn,
                        "radio rx 0",
                        listOf("ok".toRegex(), "invalid_param".toRegex(), "busy".toRegex())
                    ) == null
                ) {
                    Thread.sleep(1000)
                    continue
                }

                conn.read(Duration.ofMinutes(10), this::isInterrupted)
                if (!conn.hasCompleteText && !conn.isCurrentlyReading()) {
                    logger.info { "timeout or interrupted while in RX-mode" }
                    // timeout or interrupt
                    cmd(
                        conn,
                        "radio rxstop",
                        listOf("ok".toRegex())
                    )
                }
            }
        }
    }

    private fun cmd(connection: Connection, cmd: String, vararg expectedResponses: List<Regex>): String? {
        logger.info { "Sending: $cmd" }
        connection.outputStream.write("$cmd\r\n".toByteArray())

        var response: String? = null

        val allMatch = expectedResponses.all { possibleResponses ->
            logger.info { "Trying to read one of: $possibleResponses" }
            while (true) {
                connection.read(Duration.ofSeconds(5)) { false }
                response = if (connection.hasCompleteText) {
                    connection.getText()
                } else {
                    error("No response received while waiting for $cmd")
                }
                if (possibleResponses.none { response!!.matches(it) }) {
                    logger.info { "Sinked text: $response" }
                } else
                    break
            }
            logger.info { "Got: $response" }
            possibleResponses.first().matches(response!!)
        }

        return if (allMatch) {
            response
        } else {
            null
        }
    }
}

val headerLength = 7
val maxPayload = 255 - headerLength
val localAddr = 1

data class LoRaOutboundPacketRequest(
    val toAddr: Int,
    val type: LoRaPacketType,
    val payload: ByteArray,
    val onResult: (Boolean) -> Unit
) {
    fun toHex(): String {
        val data = ByteArray(7 + payload.size)
        data[0] = toAddr.toByte()
        data[1] = localAddr.toByte()
        data[2] = type.value.toByte()

        System.arraycopy(
            ByteBuffer.allocate(4).putInt(payload.size).array(),
            0,
            data,
            3,
            4
        )
        System.arraycopy(payload, 0, data, 7, payload.size)

        return data.toHexString()
    }

    init {
        check(payload.size <= maxPayload) { "Payload too large" }
    }
}

data class LoRaInboundPacket(
    val rssi: Int,
    val data: ByteArray,
    val originalText: String
) {

    // format: <to: uint_8><from: uint_8><type: uint_8><len: uint_32><data: uint_8[len]> (255 - 7)
    fun getToAddr() = data[0].toInt()
    fun getFromAddr() = data[1].toInt()
    fun getType() = LoRaPacketType.fromByte(data[2])
    fun getLength() = readInt32Bits(data, 3)
    fun getPayload() = data.copyOfRange(7, 7 + getLength())

    override fun toString(): String {
        return "LoRaPacket(rssi=$rssi, originalText='$originalText')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoRaInboundPacket

        if (rssi != other.rssi) return false
        if (originalText != other.originalText) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rssi
        result = 31 * result + originalText.hashCode()
        return result
    }
}

enum class LoRaPacketType(
    val value: Int
) {
    REQUEST_PING(0),
    RESPONSE_PONG(1);

    companion object {
        fun fromByte(byte: Byte) = values().first { it.value == byte.toInt() }
    }
}

class Connection(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    var hasCompleteText: Boolean = false,
) : Closeable {

    private val byteBuffer = ByteBuffer.allocate(1024)

    fun read(timeout: Duration, isinterrupted: () -> Boolean) {
        val deadLineNanos = System.nanoTime() + timeout.toNanos()

        while (System.nanoTime() < deadLineNanos) {
            if (isinterrupted() && byteBuffer.position() == 0) {
                break
            }

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
                hasCompleteText = true
                byteBuffer.flip()
                break
            }
            byteBuffer.put(i.toByte())
        }
    }

    fun isCurrentlyReading() = byteBuffer.position() != 0

    fun getText(): String {
        val arr = ByteArray(byteBuffer.remaining())
        byteBuffer.get(arr)
        byteBuffer.compact()
        hasCompleteText = false
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