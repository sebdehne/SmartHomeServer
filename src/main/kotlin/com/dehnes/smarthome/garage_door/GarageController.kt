package com.dehnes.smarthome.garage_door

import com.dehnes.smarthome.api.dtos.DoorStatus
import com.dehnes.smarthome.api.dtos.FirmwareUpgradeState
import com.dehnes.smarthome.api.dtos.GarageResponse
import com.dehnes.smarthome.api.dtos.GarageStatus
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.environment_sensors.FirmwareDataRequest
import com.dehnes.smarthome.environment_sensors.FirmwareHolder
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.lora.LoRaInboundPacketDecrypted
import com.dehnes.smarthome.lora.LoRaPacketType
import com.dehnes.smarthome.lora.maxPayload
import com.dehnes.smarthome.utils.*
import mu.KotlinLogging
import java.time.Clock
import java.util.*
import java.util.concurrent.*
import java.util.zip.CRC32
import kotlin.math.min

class GarageController(
    private val loRaConnection: LoRaConnection,
    private val clock: Clock,
    private val influxDBClient: InfluxDBClient,
    executorService: ExecutorService
) : AbstractProcess(executorService, 30) {

    private val loRaAddr = 17
    private val logger = KotlinLogging.logger { }
    private val receiveQueue = LinkedBlockingQueue<LoRaInboundPacketDecrypted>()
    private val defaultAutoCloseInSeconds = 60 * 30

    @Volatile
    private var lastStatus: GarageStatus? = null

    @Volatile
    private var keyId: Int = 1

    @Volatile
    private var firmwareDataRequest: FirmwareDataRequest? = null

    @Volatile
    private var firmwareHolder: FirmwareHolder? = null

    val listeners = ConcurrentHashMap<String, (GarageResponse) -> Unit>()

    init {
        loRaConnection.listeners.add { packet ->
            if (packet.from != loRaAddr) {
                false
            } else {
                keyId = packet.keyId

                if (packet.type == LoRaPacketType.FIRMWARE_DATA_REQUEST) {
                    onFirmwareDataRequest(packet)
                } else {
                    receiveQueue.offer(packet)
                }

                true
            }
        }
    }

    fun startFirmwareUpgrade(firmwareBased64Encoded: String) = asLocked {
        val firmwareHolder = FirmwareHolder(
            "unknown.file.name",
            Base64.getDecoder().decode(firmwareBased64Encoded)
        )
        this.firmwareHolder = firmwareHolder

        val byteArray = ByteArray(8) { 0 }

        // totalLength: 4 bytes
        System.arraycopy(
            (firmwareHolder.data.size).to32Bit(),
            0,
            byteArray,
            0,
            4
        )

        // crc32: 4 bytes
        val crc32 = CRC32()
        crc32.update(firmwareHolder.data)
        val crc32Value = crc32.value
        System.arraycopy(
            crc32Value.to32Bit(),
            0,
            byteArray,
            4,
            4
        )

        var sent = false
        val ct = CountDownLatch(1)
        receiveQueue.clear()
        loRaConnection.send(
            keyId,
            loRaAddr,
            LoRaPacketType.FIRMWARE_INFO_RESPONSE,
            byteArray,
        ) {
            ct.countDown()
            sent = it
        }
        ct.await(2, TimeUnit.SECONDS)

        sent
    } ?: false

    fun updateAutoCloseAfter(autoCloseDeltaInSeconds: Long) {
        asLocked {
            val garageStatus = lastStatus
            if (garageStatus?.autoCloseAfter != null) {
                lastStatus = garageStatus.copy(
                    autoCloseAfter = garageStatus.autoCloseAfter + (autoCloseDeltaInSeconds * 1000)
                )
            }
        }
    }

    fun adjustTime() = asLocked {
        val ct = CountDownLatch(1)
        receiveQueue.clear()
        loRaConnection.send(
            keyId,
            loRaAddr,
            LoRaPacketType.ADJUST_TIME_REQUEST,
            byteArrayOf(),
        ) {
            ct.countDown()
        }
        ct.await(2, TimeUnit.SECONDS)
        receiveQueue.poll(2, TimeUnit.SECONDS)?.type == LoRaPacketType.ADJUST_TIME_RESPONSE
    } ?: false

    fun getCurrentState() = asLocked {
        firmwareDataRequest?.let {
            GarageResponse(
                firmwareUpgradeState = FirmwareUpgradeState(
                    firmwareHolder?.data?.size ?: 0,
                    it.offset,
                    it.timestampDelta,
                    it.receivedAt,
                    it.rssi
                )
            )
        } ?: GarageResponse(lastStatus)
    }!!

    fun sendCommand(doorCommandOpen: Boolean) = asLocked {
        val currentStatus = lastStatus

        logger.info { "sendCommand doorCommandOpen=$doorCommandOpen current=$currentStatus" }

        if (currentStatus == null) {
            return@asLocked false
        }

        val autoCloseAfter: Long?
        val sent: Boolean
        val newStatus = if (doorCommandOpen) {
            sent = sendCommandInternal(true)
            autoCloseAfter = System.currentTimeMillis() + (defaultAutoCloseInSeconds * 1000)
            DoorStatus.doorOpening
        } else {
            sent = sendCommandInternal(false)
            autoCloseAfter = null
            DoorStatus.doorClosing
        }

        lastStatus = currentStatus.copy(
            doorStatus = newStatus,
            autoCloseAfter = autoCloseAfter
        )

        onStatusChanged()

        sent
    }!!

    private fun onFirmwareDataRequest(packet: LoRaInboundPacketDecrypted) {

        val firmwareDataRequest = FirmwareDataRequest(
            readInt32Bits(packet.payload, 0),
            readInt32Bits(packet.payload, 4),
            packet.payload[8].toUnsignedInt(),
            packet.timestampDelta,
            clock.millis(),
            packet.rssi
        )

        logger.info { "Handling firmwareDataRequest from ${packet.from} - $firmwareDataRequest" }

        firmwareHolder?.let {

            if (firmwareDataRequest.offset > it.data.size) {
                error("Requested offset too large")
            }

            var bytesSent = 0
            var sequentNumber = 0
            while (bytesSent < firmwareDataRequest.length) {
                val nextChunkSize = min(
                    min(
                        maxPayload - 1, // sequentNumber 1 byte
                        firmwareDataRequest.maxBytesPerResponse
                    ),
                    firmwareDataRequest.length - bytesSent
                )

                val data = ByteArray(nextChunkSize + 1)

                // write sequentNumber
                data[0] = sequentNumber.toByte()

                // write firmware bytes
                System.arraycopy(
                    it.data,
                    firmwareDataRequest.offset + bytesSent,
                    data,
                    1, // after sequentNumber
                    nextChunkSize
                )

                // send chunk
                loRaConnection.send(packet.keyId, packet.from, LoRaPacketType.FIRMWARE_DATA_RESPONSE, data) { }

                bytesSent += nextChunkSize
                sequentNumber++
            }

        } ?: logger.error { "No firmware present" }

        this.firmwareDataRequest = firmwareDataRequest
        onStatusChanged()
    }

    private fun onStatusChanged() {
        logger.info { "New status=$lastStatus firmwareUpgrade=$firmwareDataRequest" }

        influxDBClient.recordSensorData(
            "garageStatus",
            listOf(
                "light" to lastStatus!!.lightIsOn.toInt().toString(),
                "door" to lastStatus!!.doorStatus.influxDbValue.toString()
            )
        )

        listeners.forEach {
            try {
                it.value(getCurrentState())
            } catch (e: Exception) {
                logger.error("", e)
            }
        }
    }

    override fun tickLocked(): Boolean {
        val firmwareDataRequest = this.firmwareDataRequest
        if (firmwareDataRequest != null && firmwareDataRequest.receivedAt > clock.millis() - (30 * 1000)) {
            logger.info { "Not requesting data - in firmware upgrade" }
        } else {
            this.firmwareDataRequest = null

            var retryCount = 5
            while (--retryCount > 0) {
                var sent = false
                val ct = CountDownLatch(1)
                receiveQueue.clear()
                loRaConnection.send(keyId, loRaAddr, LoRaPacketType.GARAGE_HEATER_DATA_REQUEST, byteArrayOf()) {
                    ct.countDown()
                    sent = it
                }
                ct.await(2, TimeUnit.SECONDS)

                if (sent) {
                    logger.info { "Sent data request" }
                    val dataResponse = receiveQueue.poll(2, TimeUnit.SECONDS)
                    if (dataResponse != null) {
                        handleNewData(dataResponse)
                        break
                    }
                } else {
                    logger.info { "Could not send data request" }
                }

                Thread.sleep(1000)
            }
        }

        return true
    }

    private fun handleNewData(dataResponse: LoRaInboundPacketDecrypted) {
        logger.info { "Received: $dataResponse" }
        val receivedDoorStatus =
            DoorStatus.parse(dataResponse.payload[4].toInt() > 0, dataResponse.payload[5].toInt() > 0)
        val receivedLightIsOn = dataResponse.payload[6].toInt() == 0

        val currentStatus = lastStatus
        if (currentStatus == null) {
            lastStatus = GarageStatus(
                receivedLightIsOn,
                receivedDoorStatus,
                null,
                clock.timestampSecondsSince2000() - dataResponse.timestampSecondsSince2000,
                dataResponse.payload[8].toInt(),
                clock.millis()
            )
        } else {
            var autoCloseAfter = currentStatus.autoCloseAfter
            var newStatus = if (receivedDoorStatus == DoorStatus.doorClosed)
                DoorStatus.doorClosed
            else
                DoorStatus.doorOpen

            when {
                currentStatus.doorStatus == DoorStatus.doorClosed && receivedDoorStatus == DoorStatus.doorClosed -> {
                }
                currentStatus.doorStatus == DoorStatus.doorClosed && receivedDoorStatus == DoorStatus.doorOpen -> {
                    // open with another remote? accept
                    autoCloseAfter = clock.millis() + (defaultAutoCloseInSeconds * 1000)
                }
                currentStatus.doorStatus == DoorStatus.doorOpen && receivedDoorStatus == DoorStatus.doorClosed -> {
                    // closed with another remote? accept
                    autoCloseAfter = null
                }
                currentStatus.doorStatus == DoorStatus.doorOpen && receivedDoorStatus == DoorStatus.doorOpen -> {
                    if (autoCloseAfter != null && clock.millis() > autoCloseAfter) {
                        // close now
                        sendCommandInternal(false)
                        newStatus = DoorStatus.doorClosing
                        autoCloseAfter = null
                    } else if (autoCloseAfter == null) {
                        autoCloseAfter = clock.millis() + (defaultAutoCloseInSeconds * 1000)
                    }
                }
                currentStatus.doorStatus == DoorStatus.doorOpening && receivedDoorStatus == DoorStatus.doorClosed -> {
                    // OK - do not implement auto-retry for open - giveup
                    autoCloseAfter = null
                }
                currentStatus.doorStatus == DoorStatus.doorOpening && receivedDoorStatus == DoorStatus.doorOpen -> {
                    // OK - reach desired state
                }
                currentStatus.doorStatus == DoorStatus.doorClosing && receivedDoorStatus == DoorStatus.doorClosed -> {
                    // OK - reach desired state
                }
                currentStatus.doorStatus == DoorStatus.doorClosing && receivedDoorStatus == DoorStatus.doorOpen -> {
                    // OK - retry
                    sendCommandInternal(false)
                    newStatus = DoorStatus.doorClosing
                }
            }

            lastStatus = currentStatus.copy(
                doorStatus = newStatus,
                autoCloseAfter = autoCloseAfter,
                lightIsOn = receivedLightIsOn,
                utcTimestampInMs = clock.millis(),
                firmwareVersion = dataResponse.payload[8].toInt()
            )
        }

        onStatusChanged()
    }

    private fun sendCommandInternal(openCommand: Boolean): Boolean {
        val ct = CountDownLatch(1)
        receiveQueue.clear()
        loRaConnection.send(
            keyId,
            loRaAddr,
            if (openCommand) LoRaPacketType.GARAGE_DOOR_OPEN_REQUEST else LoRaPacketType.GARAGE_DOOR_CLOSE_REQUEST,
            byteArrayOf(),
        ) {
            ct.countDown()
        }
        ct.await(2, TimeUnit.SECONDS)

        return receiveQueue.poll(2, TimeUnit.SECONDS)?.type == LoRaPacketType.GARAGE_DOOR_RESPONSE
    }

    override fun logger() = logger
}

