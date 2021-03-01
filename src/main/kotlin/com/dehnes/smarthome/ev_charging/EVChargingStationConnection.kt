package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.api.dtos.EvChargingStationClient
import com.dehnes.smarthome.api.dtos.ProximityPilotAmps
import com.dehnes.smarthome.utils.PersistenceService
import mu.KotlinLogging
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32

class EVChargingStationConnection(
    private val port: Int,
    private val executorService: ExecutorService,
    private val persistenceService: PersistenceService
) {
    private val logger = KotlinLogging.logger { }
    private val chargerStationLogger = KotlinLogging.logger("ChargerStationLogger")
    private var serverSocket: ServerSocket? = null
    private val connectedClientsById = ConcurrentHashMap<String, ClientContext>()
    private val timer = Executors.newSingleThreadScheduledExecutor()

    val listeners = ConcurrentHashMap<String, (Event) -> Unit>()

    fun start() {
        serverSocket = ServerSocket(port)

        val thread = Thread {
            while (true) {
                try {
                    while (true) {
                        val clientSocket = serverSocket!!.accept()
                        logger.info { "New connection from ${clientSocket.toAddressString()}" }
                        onNewClient(clientSocket)
                    }
                } catch (t: Throwable) {
                    logger.error("", t)
                }
                Thread.sleep(5000)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    fun getConnectedClients() = connectedClientsById.values.map { it.evChargingStationClient }

    fun ping(clientId: String) = doWithClient(clientId) { socket, getResponse ->
        send(socket, Ping())
        getResponse() is PongResponse
    }

    fun uploadFirmwareAndReboot(clientId: String, firmware: ByteArray) {
        doWithClient(clientId) { socket, inboundQueue ->
            send(socket, Firmware(firmware))
        }
    }

    fun collectData(clientId: String) = doWithClient(clientId) { socket, getResponse ->
        send(socket, CollectDataRequest())
        getResponse() as DataResponse
    }!!

    fun setPwmPercent(clientId: String, pwmPercent: Int) = try {
        check(pwmPercent in 0..100) { "Invalid pwn percent $pwmPercent" }
        doWithClient(clientId) { socket, getResponse ->
            send(socket, SetPwmPercent(pwmPercent))
            getResponse() as SetPwmPercentResponse
            true
        }!!
    } catch (e: Exception) {
        logger.error("Could not send pwm percent", e)
        false
    }

    fun setContactorState(clientId: String, newState: Boolean) = try {
        doWithClient(clientId) { socket, getResponse ->
            send(socket, SetContactorState(newState))
            getResponse() as SetContactorStateResponse
            true
        }!!
    } catch (e: Exception) {
        logger.error("Could not send pwm percent", e)
        false
    }

    private fun send(socket: Socket, outboundPacket: OutboundPacket) {
        val message = outboundPacket.serializedMessage()

        // type - 1 byte
        socket.getOutputStream().write(outboundPacket.type.value)

        // len - 4 byte
        socket.getOutputStream().write(
            ByteBuffer.allocate(4).putInt(message.size).array()
        )

        // msg
        socket.getOutputStream().write(message)

        socket.getOutputStream().flush()
    }

    private fun <T> doWithClient(clientId: String, fn: (socket: Socket, getResponse: () -> InboundPacket) -> T) =
        connectedClientsById[clientId]?.let { clientContext ->
            synchronized(clientContext) {
                clientContext.lastRequestSent.set(System.currentTimeMillis())
                try {
                    fn(clientContext.socket) {
                        clientContext.inboundQueue.poll(5, TimeUnit.SECONDS)
                            ?: error("Error while waiting for response")
                    }
                } catch (t: Throwable) {
                    logger.info(
                        "Got error against client=$clientId inet=${clientContext.socket.toAddressString()} - closing socket",
                        t
                    )
                    closeContext(clientId)
                    null
                }
            }
        }

    private fun closeContext(clientId: String) {
        connectedClientsById.remove(clientId)?.let { clientContext ->
            clientContext.scheduledFuture.cancel(false)
            closeSocket(clientContext.socket)

            executorService.submit {
                listeners.values.forEach { fn ->
                    try {
                        fn(Event(EventType.closedClientConnection, clientContext.evChargingStationClient, null))
                    } catch (e: Exception) {
                        logger.error("", e)
                    }
                }
            }
        }
    }

    private fun closeSocket(socket: Socket) {
        try {
            socket.getInputStream().close()
        } catch (i: Exception) {
        }
        try {
            socket.getOutputStream().close()
        } catch (i: Exception) {
        }
        try {
            socket.close()
        } catch (i: Exception) {
        }
    }

    private fun onNewClient(socket: Socket) {
        try {

            // clientId 16 bytes
            val clientId = ByteArray(16)
            clientId.indices.forEach { i ->
                val value = socket.getInputStream().read()
                if (value < 0) {
                    closeSocket(socket)
                    logger.info { "Timeout while reading client ID. Closing socket" }
                    return
                }
                clientId[i] = value.toByte()
            }

            val firmwareVersion = socket.getInputStream().read()
            if (firmwareVersion < 0) {
                closeSocket(socket)
                logger.info { "Timeout while reading firmwareVersion. Closing socket" }
                return
            }

            val clientIdStr = clientId.toHexString().let { id ->
                persistenceService["ChargerName.$id", id]!!
            }

            val evChargingStationClient = EvChargingStationClient(
                clientIdStr,
                persistenceService["ChargerDisplayName.$clientIdStr", clientIdStr]!!,
                socket.inetAddress.toString(),
                socket.port,
                firmwareVersion,
                persistenceService.get("ChargePowerConnection.$clientIdStr", "unknown")!!,
                System.currentTimeMillis()
            )

            val inboundQueue: BlockingQueue<InboundPacket> = LinkedBlockingQueue()

            // reader thread
            executorService.submit {
                logger.info { "New client connected $evChargingStationClient" }

                try {
                    while (true) {

                        // type
                        var read = socket.getInputStream().read()
                        check(read >= 0) { "Client disconnected" }
                        val responseType = InboundType.fromValue(read)

                        // len- 4 bytes
                        val allocate = ByteBuffer.allocate(4)
                        while (allocate.hasRemaining()) {
                            read = socket.getInputStream().read()
                            check(read >= 0) { "Client disconnected" }
                            allocate.put(read.toByte())
                        }
                        val len = allocate.getInt(0)

                        // msg
                        val msg = ByteArray(len)
                        var bytesRead = 0
                        while (bytesRead < msg.size) {
                            read = socket.getInputStream().read(msg, bytesRead, msg.size - bytesRead)
                            check(read >= 0) { "Client disconnected $read" }
                            bytesRead += read
                        }

                        val inboundPacket = responseType.parser(msg)

                        if (inboundPacket.type == InboundType.notifyDataChanged) {
                            executorService.submit {
                                collectDataAndDistribute(evChargingStationClient)
                            }
                        } else {
                            inboundQueue.offer(inboundPacket)
                        }
                    }
                } catch (e: Exception) {
                    logger.info { "Connection error for client $evChargingStationClient" }
                    closeContext(clientIdStr)
                }
            }

            // keep-alive timer
            val timerRef = timer.scheduleAtFixedRate(
                {
                    executorService.submit {
                        collectDataAndDistribute(evChargingStationClient)
                    }
                },
                5,
                5,
                TimeUnit.SECONDS
            )

            connectedClientsById[clientIdStr] =
                ClientContext(socket, timerRef, evChargingStationClient, AtomicLong(), inboundQueue)

            executorService.submit {
                listeners.values.forEach { fn ->
                    try {
                        fn(Event(EventType.newClientConnection, evChargingStationClient, null))
                    } catch (e: Exception) {
                        logger.error("", e)
                    }
                }
            }

        } catch (t: Throwable) {
            logger.info("", t)
        }
    }

    private fun collectDataAndDistribute(evChargingStationClient: EvChargingStationClient) {
        val data = collectData(evChargingStationClient.clientId)
        logger.info { "Data response for ${evChargingStationClient.clientId} $data" }
        data.logMessages.forEach { msg -> chargerStationLogger.info { "charger=${evChargingStationClient.clientId} msg=$msg" } }
        executorService.submit {
            listeners.forEach { entry ->
                entry.value(Event(EventType.clientData, evChargingStationClient, data))
            }
        }
    }

}

fun Socket.toAddressString() = "${this.inetAddress}:${this.port}"

class ClientContext(
    val socket: Socket,
    val scheduledFuture: ScheduledFuture<*>,
    val evChargingStationClient: EvChargingStationClient,
    val lastRequestSent: AtomicLong = AtomicLong(),
    val inboundQueue: BlockingQueue<InboundPacket>
)

enum class EventType {
    newClientConnection,
    closedClientConnection,
    clientData
}

data class Event(
    val eventType: EventType,
    val evChargingStationClient: EvChargingStationClient,
    val clientData: DataResponse?
)

enum class RequestType(val value: Int) {
    pingRequest(1),
    firmwareUpdate(2),
    collectData(3),
    setPwmPercent(4),
    setContactorState(5),
}

enum class InboundType(val value: Int, val parser: (ByteArray) -> InboundPacket) {
    pongResponse(1, PongResponse.Companion::parse),
    collectDataResponse(2, DataResponse.Companion::parse),
    setPwmPercentResponse(3, SetPwmPercentResponse.Companion::parse),
    setContactorStateResponse(4, SetContactorStateResponse.Companion::parse),
    notifyDataChanged(100, NotifyDataChanged.Companion::parse);

    companion object {
        fun fromValue(value: Int) = values().first { it.value == value }
    }
}

sealed class InboundPacket(
    val type: InboundType
)

class PongResponse : InboundPacket(InboundType.pongResponse) {
    companion object {
        fun parse(msg: ByteArray) = PongResponse()
    }
}

class NotifyDataChanged : InboundPacket(InboundType.notifyDataChanged) {
    companion object {
        fun parse(msg: ByteArray) = NotifyDataChanged()
    }
}

class SetPwmPercentResponse : InboundPacket(InboundType.setPwmPercentResponse) {
    companion object {
        fun parse(msg: ByteArray) = SetPwmPercentResponse()
    }
}

class SetContactorStateResponse : InboundPacket(InboundType.setContactorStateResponse) {
    companion object {
        fun parse(msg: ByteArray) = SetContactorStateResponse()
    }
}


data class DataResponse(
    val conactorOn: Boolean,
    val pwmPercent: Int,
    val pilotVoltage: PilotVoltage,
    val proximityPilotAmps: ProximityPilotAmps,
    val phase1Millivolts: Int,
    val phase2Millivolts: Int,
    val phase3Millivolts: Int,
    val phase1Milliamps: Int,
    val phase2Milliamps: Int,
    val phase3Milliamps: Int,
    val wifiRSSI: Int,
    val systemUptime: Int,
    val logMessages: List<String>,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
) : InboundPacket(InboundType.pongResponse) {
    companion object {
        fun parse(msg: ByteArray): DataResponse {

            /*
             * [field]            | size in bytes
             * contactor          | 1
             * pwmPercent         | 1
             * pilotVoltage       | 1
             * proximityPilotAmps | 1
             * phase1Millivolts   | 4
             * phase2Millivolts   | 4
             * phase3Millivolts   | 4
             * phase1Milliamps    | 4
             * phase2Milliamps    | 4
             * phase3Milliamps    | 4
             * wifi RSSI          | 4 (signed int)
             * system uptime      | 4
             * logBuffer          | remaining
             */

            val logMessages = mutableListOf<String>()
            val stringBuilder = StringBuilder()
            for (i in 36 until msg.size) {
                val c = msg[i].toInt()
                if (c == 0) {
                    logMessages.add(stringBuilder.toString())
                    stringBuilder.clear()
                } else {
                    stringBuilder.append(c.toChar())
                }
            }
            if (stringBuilder.isNotEmpty()) {
                logMessages.add(stringBuilder.toString())
            }

            return DataResponse(
                msg[0].toInt() == 1,
                msg[1].toInt(),
                PilotVoltage.values().first { it.value == msg[2].toInt() },
                ProximityPilotAmps.values().first { it.value == msg[3].toInt() },
                readInt32Bits(msg, 4), // phase1Millivolts
                readInt32Bits(msg, 8), // phase2Millivolts
                readInt32Bits(msg, 12), // phase3Millivolts
                readInt32Bits(msg, 16), // phase1Milliamps
                readInt32Bits(msg, 20), // phase2Milliamps
                readInt32Bits(msg, 24), // phase3Milliamps
                readInt32Bits(msg, 28), // rssi
                readInt32Bits(msg, 32), // uptime
                logMessages
            )
        }
    }

    override fun toString(): String {
        return "DataResponse(" +
                "conactorOn=$conactorOn, " +
                "pwmPercent=$pwmPercent, " +
                "pilotVoltage=$pilotVoltage, " +
                "proximityPilotAmps=$proximityPilotAmps, " +
                "phase1Millivolts=$phase1Millivolts, " +
                "phase2Millivolts=$phase2Millivolts, " +
                "phase3Millivolts=$phase3Millivolts, " +
                "phase1Milliamps=$phase1Milliamps, " +
                "phase2Milliamps=$phase2Milliamps, " +
                "phase3Milliamps=$phase3Milliamps, " +
                "wifiRSSI=$wifiRSSI, " +
                "systemUptime=$systemUptime)"
    }

    fun measuredCurrentInAmp() = listOf(phase1Milliamps, phase2Milliamps, phase3Milliamps).maxOrNull()!! / 1000

}

fun readInt32Bits(buf: ByteArray, offset: Int): Int {
    val byteBuffer = ByteBuffer.allocate(4)
    byteBuffer.put(buf, offset, 4)
    byteBuffer.flip()
    return byteBuffer.getInt(0)
}

enum class PilotVoltage(
    val value: Int
) {
    Volt_12(0),
    Volt_9(1),
    Volt_6(2),
    Volt_3(3),
    Fault(4);

}

sealed class OutboundPacket(
    val type: RequestType
) {
    abstract fun serializedMessage(): ByteArray
}

class Ping : OutboundPacket(RequestType.pingRequest) {
    override fun serializedMessage() = byteArrayOf()
}

class CollectDataRequest : OutboundPacket(RequestType.collectData) {
    override fun serializedMessage() = byteArrayOf()
}

class SetPwmPercent(
    val pwmPercent: Int
) : OutboundPacket(RequestType.setPwmPercent) {
    override fun serializedMessage() = byteArrayOf(pwmPercent.toByte())
}

class SetContactorState(
    val newState: Boolean
) : OutboundPacket(RequestType.setContactorState) {
    override fun serializedMessage() = byteArrayOf(if (newState) 1 else 0)
}

class Firmware(
    val firmware: ByteArray
) : OutboundPacket(RequestType.firmwareUpdate) {
    override fun serializedMessage(): ByteArray {
        val crc32 = CRC32()
        crc32.update(firmware)
        val crc32Value = crc32.value

        return ByteBuffer.allocate(firmware.size + 4)
            .put(crc32Value.to32Bit())
            .put(firmware)
            .array()
    }
}

fun Long.to32Bit(): ByteArray {
    val bytes = ByteArray(8)
    ByteBuffer.wrap(bytes).putLong(this)
    return bytes.copyOfRange(4, 8)
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }