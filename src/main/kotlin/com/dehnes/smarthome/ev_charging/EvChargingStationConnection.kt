package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.api.dtos.EvChargingStationClient
import com.dehnes.smarthome.api.dtos.ProximityPilotAmps
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.utils.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import java.lang.Integer.max
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import kotlin.math.ceil
import kotlin.math.roundToInt

class EvChargingStationConnection(
    private val port: Int,
    private val executorService: ExecutorService,
    private val persistenceService: PersistenceService,
    private val influxDBClient: InfluxDBClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
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

    fun uploadFirmwareAndReboot(clientId: String, firmware: ByteArray): Boolean {
        return doWithClient(clientId) { socket, inboundQueue ->
            send(socket, Firmware(firmware))
            true
        } ?: false
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

                        val inboundPacket = responseType.parser(
                            msg,
                            getCalibrationData(clientIdStr),
                            evChargingStationClient.firmwareVersion,
                            clock.millis()
                        )

                        if (inboundPacket.type == InboundType.notifyDataChanged) {
                            executorService.submit {
                                collectDataAndDistribute(evChargingStationClient.clientId)
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
                    collectDataAndDistribute(evChargingStationClient.clientId)
                },
                0,
                2,
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

    fun collectDataAndDistribute(clientId: String) {
        executorService.submit {
            val evChargingStationClient = connectedClientsById[clientId]?.evChargingStationClient ?: return@submit
            val data = collectData(clientId)
            logger.info { "Data response for $clientId $data" }
            data.logMessages.forEach { msg -> chargerStationLogger.info { "charger=$clientId msg=$msg" } }
            recordData(data, evChargingStationClient)
            executorService.submit {
                listeners.forEach { entry ->
                    entry.value(Event(EventType.clientData, evChargingStationClient, data))
                }
            }
        }
    }

    private fun getCalibrationData(clientId: String): CalibrationData {
        val default = objectMapper.writeValueAsString(CalibrationData())
        return persistenceService["Charger.calibrationData.$clientId", default].let {
            objectMapper.readValue(it!!)
        }
    }

    private fun recordData(dataResponse: DataResponse, evChargingStationClient: EvChargingStationClient) {
        influxDBClient.recordSensorData(
            InfluxDBRecord(
                Instant.ofEpochMilli(dataResponse.utcTimestampInMs),
                "evChargingDataV2",
                mapOf(
                    "conactorOn" to dataResponse.conactorOn.toInt(),
                    "pwmPercent" to dataResponse.pwmPercent,
                    "pilotVoltage" to dataResponse.pilotVoltage.voltValue,
                    "proximityPilotAmps" to dataResponse.proximityPilotAmps.ampValue,
                    "phase1Millivolts" to dataResponse.phase1Millivolts,
                    "phase2Millivolts" to dataResponse.phase2Millivolts,
                    "phase3Millivolts" to dataResponse.phase3Millivolts,
                    "phase1Milliamps" to dataResponse.phase1Milliamps,
                    "phase2Milliamps" to dataResponse.phase2Milliamps,
                    "phase3Milliamps" to dataResponse.phase3Milliamps,
                    "phase1VoltsAdc" to dataResponse.phase1VoltsAdc,
                    "phase2VoltsAdc" to dataResponse.phase2VoltsAdc,
                    "phase3VoltsAdc" to dataResponse.phase3VoltsAdc,
                    "phase1AmpsAdc" to dataResponse.phase1AmpsAdc,
                    "phase2AmpsAdc" to dataResponse.phase2AmpsAdc,
                    "phase3AmpsAdc" to dataResponse.phase3AmpsAdc,
                    "wifiRSSI" to dataResponse.wifiRSSI,
                    "systemUptime" to dataResponse.systemUptime
                ),
                mapOf(
                    "clientId" to evChargingStationClient.clientId
                )
            )
        )
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

enum class InboundType(val value: Int, val parser: (ByteArray, CalibrationData, Int, Long) -> InboundPacket) {
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
        fun parse(msg: ByteArray, calibrationData: CalibrationData, version: Int, timestamp: Long) = PongResponse()
    }
}

class NotifyDataChanged : InboundPacket(InboundType.notifyDataChanged) {
    companion object {
        fun parse(msg: ByteArray, calibrationData: CalibrationData, version: Int, timestamp: Long) = NotifyDataChanged()
    }
}

class SetPwmPercentResponse : InboundPacket(InboundType.setPwmPercentResponse) {
    companion object {
        fun parse(msg: ByteArray, calibrationData: CalibrationData, version: Int, timestamp: Long) =
            SetPwmPercentResponse()
    }
}

class SetContactorStateResponse : InboundPacket(InboundType.setContactorStateResponse) {
    companion object {
        fun parse(msg: ByteArray, calibrationData: CalibrationData, version: Int, timestamp: Long) =
            SetContactorStateResponse()
    }
}


data class DataResponse(
    val conactorOn: Boolean,
    val pwmPercent: Int,
    val pilotVoltage: PilotVoltage,
    val proximityPilotAmps: ProximityPilotAmps,
    val phase1Millivolts: Int,
    val phase1VoltsAdc: Int,
    val phase2Millivolts: Int,
    val phase2VoltsAdc: Int,
    val phase3Millivolts: Int,
    val phase3VoltsAdc: Int,
    val phase1Milliamps: Int,
    val phase1AmpsAdc: Int,
    val phase2Milliamps: Int,
    val phase2AmpsAdc: Int,
    val phase3Milliamps: Int,
    val phase3AmpsAdc: Int,
    val wifiRSSI: Int,
    val systemUptime: Int,
    val pilotControlAdc: Int,
    val proximityPilotAdc: Int,
    val logMessages: List<String>,
    val utcTimestampInMs: Long
) : InboundPacket(InboundType.pongResponse) {
    companion object {
        fun parse(
            msg: ByteArray,
            calibrationData: CalibrationData,
            version: Int,
            timestamp: Long
        ): DataResponse {

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
            val logMessagesFromIndex = if (version < 3) 36 else 44
            for (i in logMessagesFromIndex until msg.size) {
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
                convertAdcValue(readInt32Bits(msg, 4), calibrationData.milliVoltsL1),
                readInt32Bits(msg, 4),
                convertAdcValue(readInt32Bits(msg, 8), calibrationData.milliVoltsL2),
                readInt32Bits(msg, 8),
                convertAdcValue(readInt32Bits(msg, 12), calibrationData.milliVoltsL3),
                readInt32Bits(msg, 12),
                convertAdcValue(readInt32Bits(msg, 16), calibrationData.milliAmpsL1),
                readInt32Bits(msg, 16),
                convertAdcValue(readInt32Bits(msg, 20), calibrationData.milliAmpsL2),
                readInt32Bits(msg, 20),
                convertAdcValue(readInt32Bits(msg, 24), calibrationData.milliAmpsL3),
                readInt32Bits(msg, 24),
                readInt32Bits(msg, 28), // rssi
                readInt32Bits(msg, 32), // uptime
                if (version < 3) 0 else readInt32Bits(msg, 36), // PilotControlAdc
                if (version < 3) 0 else readInt32Bits(msg, 40), // ProximityAdc
                logMessages,
                timestamp
            )
        }

        private fun convertAdcValue(
            adcValue: Int,
            offsetAndSlopeAndDivider: OffsetAndSlopeAndDivider
        ) =
            max(
                (((adcValue - offsetAndSlopeAndDivider.offset) * offsetAndSlopeAndDivider.slope) / offsetAndSlopeAndDivider.divider),
                0
            )
    }

    fun currentInAmps() =
        ceil(listOf(phase1Milliamps, phase2Milliamps, phase3Milliamps).maxOrNull()!!.toDouble() / 1000).roundToInt()

    override fun toString(): String {
        return "DataResponse(" +
                "conactorOn=$conactorOn, " +
                "pwmPercent=$pwmPercent, " +
                "pilotVoltage=$pilotVoltage, " +
                "proximityPilotAmps=$proximityPilotAmps, " +
                "phase1Millivolts=$phase1Millivolts, " +
                "phase1VoltsAdc=$phase1VoltsAdc, " +
                "phase2Millivolts=$phase2Millivolts, " +
                "phase2VoltsAdc=$phase2VoltsAdc, " +
                "phase3Millivolts=$phase3Millivolts, " +
                "phase3VoltsAdc=$phase3VoltsAdc, " +
                "phase1Milliamps=$phase1Milliamps, " +
                "phase1AmpsAdc=$phase1AmpsAdc, " +
                "phase2Milliamps=$phase2Milliamps, " +
                "phase2AmpsAdc=$phase2AmpsAdc, " +
                "phase3Milliamps=$phase3Milliamps, " +
                "phase3AmpsAdc=$phase3AmpsAdc, " +
                "pilotControlAdc=$pilotControlAdc, " +
                "proximityPilotAdc=$proximityPilotAdc, " +
                "wifiRSSI=$wifiRSSI, " +
                "systemUptime=$systemUptime, " +
                "utcTimestampInMs=$utcTimestampInMs)"
    }

}

data class CalibrationData(
    val milliVoltsL1: OffsetAndSlopeAndDivider = OffsetAndSlopeAndDivider(),
    val milliVoltsL2: OffsetAndSlopeAndDivider = OffsetAndSlopeAndDivider(),
    val milliVoltsL3: OffsetAndSlopeAndDivider = OffsetAndSlopeAndDivider(),
    val milliAmpsL1: OffsetAndSlopeAndDivider = OffsetAndSlopeAndDivider(),
    val milliAmpsL2: OffsetAndSlopeAndDivider = OffsetAndSlopeAndDivider(),
    val milliAmpsL3: OffsetAndSlopeAndDivider = OffsetAndSlopeAndDivider()
)

data class OffsetAndSlopeAndDivider(
    val offset: Int = 0,
    val slope: Int = 1,
    val divider: Int = 1
)

enum class PilotVoltage(
    val value: Int,
    val voltValue: Int
) {
    Volt_12(0, 12),
    Volt_9(1, 9),
    Volt_6(2, 6),
    Volt_3(3, 3),
    Fault(4, 0);
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

