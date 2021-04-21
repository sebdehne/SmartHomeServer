package com.dehnes.smarthome.lora

import com.dehnes.smarthome.garage_door.toInt
import com.dehnes.smarthome.lora.LoRaPacketType.*
import com.dehnes.smarthome.utils.*
import mu.KotlinLogging
import java.lang.Integer.min
import java.time.Clock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

class LoRaSensorBoardService(
    private val loRaConnection: LoRaConnection,
    private val clock: Clock,
    private val persistenceService: PersistenceService
) {

    private val logger = KotlinLogging.logger { }
    private val sensors = mutableMapOf<Int, SensorState>()

    private var firmwareHolder: FirmwareHolder? = null

    init {
        loRaConnection.listeners.add { packet ->
            synchronized(this) {
                val currentState = sensors[packet.from] ?: SensorState(
                    packet.from,
                    0,
                    false,
                    null
                )

                val updatedState = when (packet.type) {
                    REQUEST_PING -> onPing(currentState, packet)
                    SENSOR_DATA_REQUEST -> onSensorData(currentState, packet)
                    SENSOR_FIRMWARE_INFO_REQUEST -> onFirmwareInfoRequest(currentState, packet)
                    SENSOR_FIRMWARE_DATA_REQUEST -> onFirmwareDataRequest(currentState, packet)
                    else -> null
                }

                if (updatedState != null) {
                    sensors[packet.from] = updatedState
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun onFirmwareDataRequest(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {

        val firmwareDataRequest = FirmwareDataRequest(
            readInt32Bits(packet.payload, 0),
            packet.payload[4].toInt(),
            packet.payload[5].toInt(),
            clock.timestampSecondsSince2000() - clock.millis(),
            clock.millis()
        )

        logger.info { "Handling firmwareDataRequest from ${packet.from} - $firmwareDataRequest" }

        firmwareHolder?.let {

            if (firmwareDataRequest.offset > it.data.size) {
                error("Requested offset too large")
            }

            val maxBytesPerResponse = min(firmwareDataRequest.bytesPerResponse, maxPayload - 4)
            var offset = firmwareDataRequest.offset
            var messagesSent = 0
            while (messagesSent < firmwareDataRequest.numberResponses) {
                val nextChunkSize = min(
                    maxBytesPerResponse,
                    it.data.size - offset
                )
                if (nextChunkSize <= 0) {
                    logger.info { "No more data to send" }
                    break
                }
                val data = ByteArray(nextChunkSize + 4)

                // write offset
                System.arraycopy(
                    offset.to32Bit(),
                    0,
                    data,
                    0,
                    4
                )

                // write firmware bytes
                System.arraycopy(
                    it.data,
                    offset,
                    data,
                    4, // after offset
                    nextChunkSize
                )

                // send chunk
                val sync = LinkedBlockingQueue<Boolean>()
                loRaConnection.send(packet.keyId, packet.from, SENSOR_DATA_RESPONSE, data) { sent ->
                    sync.offer(sent)
                }

                val sent = sync.poll(5, TimeUnit.SECONDS)

                if (sent == true) {
                    messagesSent++
                    offset += data.size
                } else {
                    logger.info { "Could not send firmware chunk, giving up" }
                    break
                }
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
        val sensorData = SensorData(
            readLong32Bits(packet.payload, 0),
            readLong32Bits(packet.payload, 4),
            readLong32Bits(packet.payload, 8),
            readLong32Bits(packet.payload, 12),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis()
        )
        val firmwareVersion = packet.payload[16].toInt()
        logger.info { "Handling sensorData from ${packet.from}: $sensorData" }

        val responsePayload = ByteArray(2)
        responsePayload[0] = existingState.triggerFirmwareUpdate.toInt().toByte()
        responsePayload[1] = 0 // timeAdjustmentRequired TODO

        loRaConnection.send(packet.keyId, packet.from, SENSOR_DATA_RESPONSE, responsePayload) {
            if (!it) {
                logger.info { "Could not send sensor-data response" }
            }
        }

        return existingState.copy(
            firmwareVersion = firmwareVersion,
            lastReceivedMessage = sensorData
        )
    }

    private fun onPing(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {
        // no need to validate timestamp because ping is used for initial setup of the RTC
        logger.info { "Handling ping from ${packet.from}" }

        val ping = Ping(
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis()
        )
        val firmwareVersion = packet.payload[0].toInt()

        loRaConnection.send(packet.keyId, packet.from, RESPONSE_PONG, packet.payload) {
            if (!it) {
                logger.info { "Could not send pong response" }
            }
        }

        return existingState.copy(
            firmwareVersion = firmwareVersion,
            lastReceivedMessage = ping
        )
    }
}

data class SensorState(
    val id: Int,
    val firmwareVersion: Int,
    val triggerFirmwareUpdate: Boolean,
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
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class Ping(
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class FirmwareInfoRequest(
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class FirmwareDataRequest(
    val offset: Int,
    val bytesPerResponse: Int,
    val numberResponses: Int,
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class FirmwareHolder(
    val filename: String,
    val data: ByteArray
)