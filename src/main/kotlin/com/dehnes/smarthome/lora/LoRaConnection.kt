package com.dehnes.smarthome.lora

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.utils.*
import mu.KotlinLogging
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class LoRaConnection(
    private val configService: ConfigService,
    private val executorService: ExecutorService,
    private val aes265GCM: AES265GCM,
    private val clock: Clock
) {

    private val readTimeInSeconds = 10L

    private val logger = KotlinLogging.logger { }

    val listeners = CopyOnWriteArrayList<(LoRaInboundPacketDecrypted) -> Boolean>()

    @Volatile
    private var isStarted = false

    private val outQueue: Queue<LoRaOutboundPacketRequest> = ConcurrentLinkedQueue()

    @Volatile
    var interrupted: Boolean = false

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

    @Synchronized
    fun stop() {
        if (!isStarted) {
            return
        }
        isStarted = false
    }

    fun send(
        keyId: Int,
        toAddr: Int,
        type: LoRaPacketType,
        payload: ByteArray,
        timestamp: Long?,
        onResult: (Boolean) -> Unit
    ) {
        val element = LoRaOutboundPacketRequest(
            keyId,
            toAddr,
            type,
            payload,
            timestamp,
            onResult
        )

        logger.debug { "LoRa outbound packet scheduled: $element" }

        outQueue.add(element)
        interrupted = true
    }

    private fun connect(): Connection {

        val serialDev = configService.getLoraSerialPort()

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
            file.outputStream(),
            clock.millis()
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
                logger.debug { "Sinked text: ${conn.getText()}" }
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

            while (cmd(
                    conn,
                    "mac pause",
                    listOf("\\d+".toRegex())
                ) == null
            ) {
                Thread.sleep(1000)
            }
            while (cmd(conn, "radio set sf sf7", listOf("ok".toRegex(), "invalid_param".toRegex())) == null) {
                Thread.sleep(1000)
            }

            while (isStarted) {

                if (clock.millis() - conn.createdAt > Duration.ofDays(10).toMillis()) {
                    logger.info { "Resetting connection to LoRa bridge" }
                    break // reset connection
                }

                if (conn.hasCompleteText) {
                    val line = conn.getText()
                    if (line == "radio_err") {
                        logger.debug { "Ignoring radio_err" }
                    } else {
                        logger.debug { "Received: '$line' (${line.toByteArray().contentToString()})" }
                        val encryptedPacket = LoRaInboundPacket(
                            cmd(conn, "radio get rssi", listOf(".*".toRegex()))?.toInt()
                                ?: error("could not read rssi"),
                            if (line.startsWith("radio_rx")) {
                                decodeHexString(line.replace("radio_rx\\s+".toRegex(), ""))
                            } else {
                                error("Unexpected response=$line")
                            },
                            line
                        )

                        val inboundPacket = decrypt(encryptedPacket)

                        if (inboundPacket?.to != localAddr) {
                            logger.debug { "Ignoring packet not for me. $inboundPacket" }
                        } else if (inboundPacket.type == LoRaPacketType.SETUP_REQUEST) {
                            onSetupRequest(inboundPacket)
                        } else if (configService.getEnvironmentSensors().validateTimestamp && (inboundPacket.timestampDelta < -30 || inboundPacket.timestampDelta > 30)) {
                            logger.warn { "Ignoring received packet because of invalid timestampDelta. $inboundPacket" }
                        } else {

                            logger.debug { "Handling received packet $inboundPacket" }

                            // do not send out any scheduled packets for this sensor
                            while (outQueue.peek()?.toAddr == inboundPacket.from) {
                                logger.info { "Dropping outbound packet ${outQueue.poll()}" }
                            }

                            executorService.submit(withLogging {
                                val wasAccepted = listeners.any { listener ->
                                    listener(inboundPacket)
                                }
                                if (!wasAccepted) {
                                    logger.warn { "No listener handled message $inboundPacket" }
                                }
                            })
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

                    val data = nextOutPacket.toByteArray(clock.timestampSecondsSince2000())
                    val cipherTextWithIv = aes265GCM.encrypt(data, nextOutPacket.keyId)

                    val result = cmd(
                        conn, "radio tx " + cipherTextWithIv.toHexString(),
                        listOf("ok".toRegex(), "invalid_param".toRegex(), "busy".toRegex()),
                        listOf("radio_tx_ok".toRegex(), "radio_err".toRegex())
                    ) != null
                    executorService.submit(withLogging {
                        nextOutPacket.onResult(result)
                    })

                    if (outQueue.peek() != null) {
                        Thread.sleep(300) // receive needs some time to process and message + switch to receive mode again
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
        logger.debug { "Sending: $cmd" }
        connection.outputStream.write("$cmd\r\n".toByteArray())

        var response: String? = null

        val allMatch = expectedResponses.all { possibleResponses ->
            logger.debug { "Trying to read one of: $possibleResponses" }
            while (true) {
                connection.read(Duration.ofSeconds(5)) { false }
                response = if (connection.hasCompleteText) {
                    connection.getText()
                } else {
                    error("No response received while waiting for $cmd")
                }
                if (possibleResponses.none { response!!.matches(it) }) {
                    logger.debug { "Sinked text: $response" }
                } else
                    break
            }
            logger.debug { "Got: $response" }
            possibleResponses.first().matches(response!!)
        }

        return if (allMatch) {
            response
        } else {
            null
        }
    }

    fun decrypt(inboundPacket: LoRaInboundPacket) = aes265GCM.decrypt(inboundPacket.data)?.let {
        /*
         * Header(11 bytes):
         * to[1]
         * from[1]
         * type[1]
         * timestamp[4]
         * len[4]
         * payload(0...len bytes)
         */
        val length = readInt32Bits(it.second, 7)
        val timestampSecondsSince2000 = readLong32Bits(it.second, 3)
        LoRaInboundPacketDecrypted(
            inboundPacket,
            it.first,
            it.second[0].toInt(),
            it.second[1].toInt(),
            LoRaPacketType.fromByte(it.second[2]),
            timestampSecondsSince2000,
            clock.timestampSecondsSince2000() - timestampSecondsSince2000,
            inboundPacket.rssi,
            it.second.copyOfRange(11, 11 + length)
        )
    }

    private fun onSetupRequest(packet: LoRaInboundPacketDecrypted) {
        val serialId = ByteArray(16)
        serialId.indices.forEach { i ->
            serialId[i] = packet.payload[i + 1]
        }
        val setupRequest = SetupRequest(
            packet.payload[0].toInt(),
            serialId.toHexString(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis(),
            packet.rssi
        )

        logger.info { "Handling setupRequest=$setupRequest" }

        val sensorId = getSensor(setupRequest.serialIdHex)?.id
        if (sensorId != null) {
            send(packet.keyId, sensorId, LoRaPacketType.SETUP_RESPONSE, packet.payload, null) {
                if (!it) {
                    logger.info { "Could not send pong response" }
                }
            }
        } else {
            logger.error { "No loraAddr configured for ${setupRequest.serialIdHex}" }
        }
    }

    private fun getSensor(serialId: String) = configService.getEnvironmentSensors().sensors[serialId]

}

val headerLength = 11
val maxPayload = 255 - headerLength - AES265GCM.overhead()
val localAddr = 1

data class LoRaOutboundPacketRequest(
    val keyId: Int,
    val toAddr: Int,
    val type: LoRaPacketType,
    val payload: ByteArray,
    val timestamp: Long?,
    val onResult: (Boolean) -> Unit
) {
    fun toByteArray(timestamp: Long): ByteArray {
        val data = ByteArray(11 + payload.size)
        data[0] = toAddr.toByte()
        data[1] = localAddr.toByte()
        data[2] = type.value.toByte()

        System.arraycopy(
            (this.timestamp ?: timestamp).to32Bit(),
            0,
            data,
            3,
            4
        )

        System.arraycopy(
            payload.size.to32Bit(),
            0,
            data,
            7,
            4
        )

        System.arraycopy(payload, 0, data, 11, payload.size)

        return data
    }

    init {
        check(payload.size <= maxPayload) { "Payload too large" }
    }
}

data class LoRaInboundPacketDecrypted(
    val originalPacket: LoRaInboundPacket,
    val keyId: Int,
    val to: Int,
    val from: Int,
    val type: LoRaPacketType,
    val timestampSecondsSince2000: Long,
    val timestampDelta: Long,
    val rssi: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoRaInboundPacketDecrypted

        if (originalPacket != other.originalPacket) return false
        if (keyId != other.keyId) return false
        if (to != other.to) return false
        if (from != other.from) return false
        if (type != other.type) return false
        if (timestampSecondsSince2000 != other.timestampSecondsSince2000) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = originalPacket.hashCode()
        result = 31 * result + keyId
        result = 31 * result + to
        result = 31 * result + from
        result = 31 * result + type.hashCode()
        result = 31 * result + timestampSecondsSince2000.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class LoRaInboundPacket(
    val rssi: Int,
    val data: ByteArray,
    val originalText: String
) {

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
    SETUP_REQUEST(0),
    SETUP_RESPONSE(1),

    SENSOR_DATA_REQUEST(2),
    SENSOR_DATA_RESPONSE(3),

    FIRMWARE_INFO_REQUEST(4),
    FIRMWARE_INFO_RESPONSE(5),
    FIRMWARE_DATA_REQUEST(6),
    FIRMWARE_DATA_RESPONSE(7),

    ADJUST_TIME_REQUEST(8),
    ADJUST_TIME_RESPONSE(9),

    GARAGE_DOOR_OPEN_REQUEST(10),
    GARAGE_DOOR_CLOSE_REQUEST(11),
    GARAGE_DOOR_RESPONSE(12),

    HEATER_ON_REQUEST(13),
    HEATER_OFF_REQUEST(14),
    HEATER_RESPONSE(15),

    GARAGE_HEATER_DATA_RESPONSE(17),

    SENSOR_DATA_REQUEST_V2(18),
    SENSOR_DATA_RESPONSE_V2(19),

    GARAGE_HEATER_DATA_RESPONSEV2(20),

    SENSOR_DATA_REQUEST_V3(21),

    GARAGE_HEATER_DATA_REQUESTV2(22)
    ;

    companion object {
        fun fromByte(byte: Byte) = values().first { it.value == byte.toInt() }
    }
}

class Connection(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val createdAt: Long,
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

abstract class ReceivedMessage {
    abstract val timestampDelta: Long
    abstract val receivedAt: Long
    abstract val rssi: Int
}

data class SetupRequest(
    val firmwareVersion: Int,
    val serialIdHex: String,
    override val timestampDelta: Long,
    override val receivedAt: Long,
    override val rssi: Int
) : ReceivedMessage()