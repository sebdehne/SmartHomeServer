package com.dehnes.smarthome.lora

import com.dehnes.smarthome.garage_door.toInt
import com.dehnes.smarthome.utils.PersistenceService
import com.dehnes.smarthome.utils.readLong32Bits
import com.dehnes.smarthome.utils.timestampSecondsSince2000
import mu.KotlinLogging
import java.time.Clock

class LoRaSensorBoardService(
    private val loRaConnection: LoRaConnection,
    private val clock: Clock,
    private val persistenceService: PersistenceService
) {

    private val logger = KotlinLogging.logger { }
    private val sensors = mutableMapOf<Int, SensorState>()

    init {
        loRaConnection.listeners.add { packet ->

            synchronized(this) {

                val currentState = sensors[packet.from] ?: SensorState(
                    packet.from,
                    null
                )

                val updatedState = when (packet.type) {
                    LoRaPacketType.REQUEST_PING -> {
                        onPing(currentState, packet)
                    }
                    LoRaPacketType.SENSOR_DATA_REQUEST -> {
                        onSensorData(currentState, packet)
                    }
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

    private fun firmwareUpdateNeeded(sensorId: Int, currentVersion: Int) =
        persistenceService["sensor.$sensorId.firmware", "1"]!!.toInt() > currentVersion

    private fun onSensorData(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {
        val sensorData = SensorData(
            readLong32Bits(packet.payload, 0),
            readLong32Bits(packet.payload, 4),
            readLong32Bits(packet.payload, 8),
            readLong32Bits(packet.payload, 12),
            packet.payload[16].toInt(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis()
        )
        logger.info { "Handling sensorData from ${packet.from}: $sensorData" }

        val responsePayload = ByteArray(2)
        responsePayload[0] = firmwareUpdateNeeded(packet.from, sensorData.firmwareVersion).toInt().toByte()
        responsePayload[1] = 0 // timeAdjustmentRequired TODO

        loRaConnection.send(packet.keyId, packet.from, LoRaPacketType.SENSOR_DATA_RESPONSE, responsePayload) {
            if (!it) {
                logger.info { "Could not send sensor-data response" }
            }
        }

        return existingState.copy(
            lastReceivedMessage = sensorData
        )
    }

    private fun onPing(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {
        // no need to validate timestamp because ping is used for initial setup of the RTC
        logger.info { "Handling ping from ${packet.from}" }

        val ping = Ping(
            packet.payload[0].toInt(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis()
        )

        loRaConnection.send(packet.keyId, packet.from, LoRaPacketType.RESPONSE_PONG, packet.payload) {
            if (!it) {
                logger.info { "Could not send pong response" }
            }
        }

        return existingState.copy(
            lastReceivedMessage = ping
        )
    }
}

data class SensorState(
    val id: Int,
    val lastReceivedMessage: ReceivedMessage?
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
    val firmwareVersion: Int,
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()

data class Ping(
    val firmwareVersion: Int,
    override val timestampDelta: Long,
    override val receivedAt: Long
) : ReceivedMessage()