package com.dehnes.smarthome.lora

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.garage_door.toInt
import com.dehnes.smarthome.lora.LoRaPacketType.*
import com.dehnes.smarthome.utils.*
import mu.KotlinLogging
import java.time.Clock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.zip.CRC32
import kotlin.math.max
import kotlin.math.min

class LoRaSensorBoardService(
    private val loRaConnection: LoRaConnection,
    private val clock: Clock,
    private val executorService: ExecutorService,
    private val persistenceService: PersistenceService
) {

    val listeners = ConcurrentHashMap<String, (EnvironmentSensorEvent) -> Unit>()

    private val logger = KotlinLogging.logger { }
    private val sensors = ConcurrentHashMap<Int, SensorState>()

    @Volatile
    private var firmwareHolder: FirmwareHolder? = null

    init {
        loRaConnection.listeners.add { packet ->
            synchronized(this) {
                val currentState = sensors[packet.from] ?: SensorState(
                    id = packet.from,
                    firmwareVersion = 0,
                    triggerFirmwareUpdate = false,
                    triggerTimeAdjustment = false,
                    lastReceivedMessage = null
                )

                val updatedState = when (packet.type) {
                    REQUEST_PING -> onPing(currentState, packet)
                    SENSOR_DATA_REQUEST -> onSensorData(currentState, packet)
                    SENSOR_FIRMWARE_INFO_REQUEST -> onFirmwareInfoRequest(currentState, packet)
                    SENSOR_FIRMWARE_DATA_REQUEST -> onFirmwareDataRequest(currentState, packet)
                    else -> null
                }

                if (updatedState != null) {
                    sensors[updatedState.id] = updatedState

                    // notify listeners
                    executorService.submit {
                        listeners.forEach { (_, fn) ->
                            fn(
                                EnvironmentSensorEvent(
                                    EnvironmentSensorEventType.update,
                                    getAllState(),
                                    getFirmwareInfo()
                                )
                            )
                        }
                    }

                    true
                } else {
                    false
                }
            }
        }
    }

    fun getEnvironmentSensorResponse() = EnvironmentSensorResponse(
        getAllState(),
        getFirmwareInfo()
    )

    private fun getFirmwareInfo() = firmwareHolder?.let {
        FirmwareInfo(
            it.filename,
            it.data.size
        )
    }

    private fun getAllState() = sensors.map { (sensorId, sensorState) ->
        val lastReceivedMessage = sensorState.lastReceivedMessage
        val firmwareUpgradeState = firmwareHolder?.let {
            when (lastReceivedMessage) {
                is FirmwareInfoRequest -> FirmwareUpgradeState(
                    it.data.size,
                    0,
                    lastReceivedMessage.timestampDelta,
                    lastReceivedMessage.receivedAt
                )
                is FirmwareDataRequest -> FirmwareUpgradeState(
                    it.data.size,
                    lastReceivedMessage.offset,
                    lastReceivedMessage.timestampDelta,
                    lastReceivedMessage.receivedAt
                )
                else -> null
            }
        }

        EnvironmentSensorState(
            sensorId,
            persistenceService["EnvironmentSensor.$sensorId.displayName", sensorId.toString()]!!,
            getSleepTimeInSeconds(sensorId),
            if (lastReceivedMessage is SensorData) EnvironmentSensorData(
                lastReceivedMessage.temperature,
                lastReceivedMessage.humidity,
                batteryAdcToMv(sensorId, lastReceivedMessage.adcBattery),
                lastReceivedMessage.adcLight,
                lastReceivedMessage.sleepTimeInSeconds,
                lastReceivedMessage.timestampDelta,
                lastReceivedMessage.receivedAt
            ) else null,
            firmwareUpgradeState,
            sensorState.firmwareVersion,
            sensorState.triggerFirmwareUpdate,
            sensorState.triggerTimeAdjustment
        )
    }

    private fun batteryAdcToMv(sensorId: Int, adcBattery: Long): Long {
        val fiveVadc = persistenceService["EnvironmentSensor.$sensorId.5vADC", "3000"]!!.toLong()
        val slope = (5000 * 1000) / fiveVadc
        return (adcBattery * slope) / 1000
    }

    fun adjustSleepTimeInSeconds(sensorId: Int, sleepTimeInSecondsDelta: Long) = synchronized(this) {
        val sensorState = sensors[sensorId]
        if (sensorState == null) {
            false
        } else {
            val newSleepTimeInSeconds = min(
                max(
                    1,
                    getSleepTimeInSeconds(sensorId) + sleepTimeInSecondsDelta
                ),
                10 * 60
            )
            setSleepTimeInSeconds(sensorId, newSleepTimeInSeconds)
            true
        }
    }

    fun firmwareUpgrade(sensorId: Int, scheduled: Boolean) = synchronized(this) {
        val sensorState = sensors[sensorId]
        if (sensorState == null) {
            false
        } else {
            sensors[sensorId] = sensorState.copy(
                triggerFirmwareUpdate = scheduled
            )
            true
        }
    }

    fun timeAdjustment(sensorId: Int, scheduled: Boolean) = synchronized(this) {
        val sensorState = sensors[sensorId]
        if (sensorState == null) {
            false
        } else {
            sensors[sensorId] = sensorState.copy(
                triggerTimeAdjustment = scheduled
            )
            true
        }
    }

    fun setFirmware(filename: String, firmwareBased64Encoded: String) {
        synchronized(this) {
            firmwareHolder = FirmwareHolder(
                filename,
                Base64.getDecoder().decode(firmwareBased64Encoded)
            )
        }
    }

    private fun onFirmwareDataRequest(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {

        val firmwareDataRequest = FirmwareDataRequest(
            readInt32Bits(packet.payload, 0),
            readInt32Bits(packet.payload, 4),
            packet.payload[8].toUnsignedInt(),
            packet.timestampDelta,
            clock.millis()
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
                loRaConnection.send(packet.keyId, packet.from, SENSOR_FIRMWARE_DATA_RESPONSE, data) { }

                bytesSent += nextChunkSize
                sequentNumber++
            }

        } ?: logger.error { "No firmware present" }

        return existingState.copy(lastReceivedMessage = firmwareDataRequest)
    }

    private fun onFirmwareInfoRequest(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {
        logger.info { "Handling firmwareInfoRequest from ${packet.from}" }

        val byteArray = ByteArray(8) { 0 }
        firmwareHolder?.let {

            // totalLength: 4 bytes
            System.arraycopy(
                (it.data.size).to32Bit(),
                0,
                byteArray,
                0,
                4
            )

            // crc32: 4 bytes
            val crc32 = CRC32()
            crc32.update(it.data)
            val crc32Value = crc32.value
            System.arraycopy(
                crc32Value.to32Bit(),
                0,
                byteArray,
                4,
                4
            )
        }

        loRaConnection.send(packet.keyId, packet.from, SENSOR_FIRMWARE_INFO_RESPONSE, byteArray) {
            if (!it) {
                logger.info { "Could not send firmware-info-response" }
            }
        }

        return existingState.copy(
            triggerFirmwareUpdate = false,
            lastReceivedMessage = FirmwareInfoRequest(
                clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
                clock.millis()
            )
        )
    }

    private fun onSensorData(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {
        if (packet.payload.size != 21) {
            logger.warn { "Forced to ignore packet with invalid payloadSize=${packet.payload.size} != 21" }
            return existingState
        }
        val sensorData = SensorData(
            readLong32Bits(packet.payload, 0),
            readLong32Bits(packet.payload, 4),
            readLong32Bits(packet.payload, 8),
            readLong32Bits(packet.payload, 12),
            readLong32Bits(packet.payload, 16),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis()
        )
        val firmwareVersion = packet.payload[20].toInt()
        logger.info { "Handling sensorData from ${packet.from}: $sensorData" }

        val responsePayload = ByteArray(6) { 0 }
        responsePayload[0] = existingState.triggerFirmwareUpdate.toInt().toByte()
        responsePayload[1] = existingState.triggerTimeAdjustment.toInt().toByte()
        val sleepTimeInSeconds = getSleepTimeInSeconds(existingState.id)
        val updatedSensorData = if (sensorData.sleepTimeInSeconds != sleepTimeInSeconds) {
            logger.info { "Sending updated sleep time. ${sensorData.sleepTimeInSeconds} -> $sleepTimeInSeconds" }
            System.arraycopy(
                sleepTimeInSeconds.to32Bit(),
                0,
                responsePayload,
                2,
                4
            )
            sensorData.copy(
                sleepTimeInSeconds = sleepTimeInSeconds
            )
        } else {
            sensorData
        }

        loRaConnection.send(packet.keyId, packet.from, SENSOR_DATA_RESPONSE, responsePayload) {
            if (!it) {
                logger.info { "Could not send sensor-data response" }
            }
        }

        return existingState.copy(
            triggerTimeAdjustment = false,
            firmwareVersion = firmwareVersion,
            lastReceivedMessage = updatedSensorData
        )
    }

    private fun onPing(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState? {
        val serialId = ByteArray(16)
        serialId.indices.forEach { i ->
            serialId[i] = packet.payload[i + 1]
        }
        val ping = Ping(
            packet.payload[0].toInt(),
            serialId.toHexString(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis()
        )

        logger.info { "Handling ping=$ping" }

        val loraAddr = getLoRaAddr(ping.serialIdHex)
        return if (loraAddr != null) {
            loRaConnection.send(packet.keyId, loraAddr, RESPONSE_PONG, packet.payload) {
                if (!it) {
                    logger.info { "Could not send pong response" }
                }
            }
            existingState.copy(
                id = loraAddr,
                firmwareVersion = ping.firmwareVersion,
                lastReceivedMessage = ping
            )
        } else {
            logger.error { "No loraAddr configured for ${ping.serialIdHex}" }
            null
        }
    }

    private fun getLoRaAddr(serialId: String) = persistenceService["EnvironmentSensor.loraAddr.$serialId", null]?.toInt()

    private fun getSleepTimeInSeconds(sensorId: Int) =
        persistenceService["EnvironmentSensor.${sensorId}.sleepTimeInSeconds", "20"]!!.toLong()

    private fun setSleepTimeInSeconds(sensorId: Int, sleepTimeInSeconds: Long) {
        persistenceService["EnvironmentSensor.${sensorId}.sleepTimeInSeconds"] = sleepTimeInSeconds.toString()
    }

}

data class SensorState(
    val id: Int,
    val firmwareVersion: Int,
    val triggerFirmwareUpdate: Boolean,
    val triggerTimeAdjustment: Boolean,
    val lastReceivedMessage: ReceivedMessage? = null
)

sealed class ReceivedMessage {
    abstract val timestampDelta: Long
    abstract val receivedAt: Long
}

data class SensorData(
    val temperature: Long,
    val humidity: Long,
    val adcBattery: Long,
    val adcLight: Long,
    val sleepTimeInSeconds: Long,
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class Ping(
    val firmwareVersion: Int,
    val serialIdHex: String,
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class FirmwareInfoRequest(
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class FirmwareDataRequest(
    val offset: Int,
    val length: Int,
    val maxBytesPerResponse: Int,
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class FirmwareHolder(
    val filename: String,
    val data: ByteArray
)