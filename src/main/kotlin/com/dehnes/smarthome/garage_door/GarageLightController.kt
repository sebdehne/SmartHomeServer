package com.dehnes.smarthome.garage_door

import com.dehnes.smarthome.api.dtos.GarageLightStatus
import com.dehnes.smarthome.api.dtos.LEDStripeStatus
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.lora.LoRaInboundPacketDecrypted
import com.dehnes.smarthome.lora.LoRaPacketType
import com.dehnes.smarthome.users.SystemUser
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.concurrent.*

class GarageController(
    private val loRaConnection: LoRaConnection,
    private val clock: Clock,
    private val influxDBClient: InfluxDBClient,
    executorService: ExecutorService,
    private val userSettingsService: UserSettingsService,
) : AbstractProcess(executorService, 30) {

    private val loRaAddr = 17
    private val logger = KotlinLogging.logger { }
    private val receiveQueue = LinkedBlockingQueue<LoRaInboundPacketDecrypted>()

    @Volatile
    private var lastStatus: GarageLightStatus? = null
    private val keyId: Int = 1

    private val listeners = ConcurrentHashMap<String, (GarageLightStatus) -> Unit>()

    init {
        loRaConnection.listeners.add { packet ->
            if (packet.from != loRaAddr) {
                false
            } else {
                if (packet.type == LoRaPacketType.GARAGE_LIGHT_NOTIFY_CEILING_LIGHT) {
                    sendCommand(LoRaPacketType.GARAGE_LIGHT_ACK, expectResponse = null)

                    asLocked {
                        lastStatus = lastStatus?.copy(
                            ceilingLightIsOn = packet.payload[0].toInt() != 0,
                            timestampDelta = packet.timestampDelta,
                            utcTimestampInMs = clock.millis(),
                        )
                        onStatusUpdated(true)
                    }
                } else {
                    receiveQueue.offer(packet)
                }
                true
            }
        }
    }

    fun addListener(user: String?, id: String, listener: (GarageLightStatus) -> Unit) {
        if (!userSettingsService.canUserRead(user, UserRole.garageDoor)) return
        listeners[id] = listener
    }

    fun removeListener(id: String) {
        listeners.remove(id)
    }

    fun getCurrentState(user: String?) = asLocked {
        if (!userSettingsService.canUserRead(user, UserRole.garageDoor)) return@asLocked null

        lastStatus
    }

    fun switchOnCeilingLight(user: String?): Boolean? = asLocked {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) return@asLocked null

        sendCommand(
            LoRaPacketType.GARAGE_LIGHT_SWITCH_CEILING_LIGHT_ON,
            expectResponse = { it.type == LoRaPacketType.GARAGE_LIGHT_ACK }).apply {
            if (this) {
                lastStatus = lastStatus?.copy(
                    ceilingLightIsOn = true
                )
                onStatusUpdated(true)
            }
        }
    }

    fun switchOffCeilingLight(user: String?): Boolean? = asLocked {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) return@asLocked null

        sendCommand(
            LoRaPacketType.GARAGE_LIGHT_SWITCH_CEILING_LIGHT_OFF,
            expectResponse = { it.type == LoRaPacketType.GARAGE_LIGHT_ACK }).apply {
            if (this) {
                lastStatus = lastStatus?.copy(
                    ceilingLightIsOn = false
                )
                onStatusUpdated(true)
            }
        }
    }

    fun switchLedStripeOff(user: String?): Boolean? = asLocked {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) return@asLocked null

        sendCommand(LoRaPacketType.GARAGE_LIGHT_SET_DAC, ByteArray(4).apply {
            write(0L.to16Bit(), 0)
            write(0L.to16Bit(), 2)
        }, expectResponse = { it.type == LoRaPacketType.GARAGE_LIGHT_ACK }).apply {
            if (this) {
                lastStatus = lastStatus?.copy(
                    ledStripeStatus = LEDStripeStatus.off
                )
                onStatusUpdated(true)
            }
        }
    }

    fun switchLedStripeOnLow(user: String?): Boolean? = asLocked {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) return@asLocked null

        sendCommand(LoRaPacketType.GARAGE_LIGHT_SET_DAC, ByteArray(4).apply {
            write(2500L.to16Bit(), 0)
            write(2500L.to16Bit(), 2)
        }, expectResponse = { it.type == LoRaPacketType.GARAGE_LIGHT_ACK }).apply {
            if (this) {
                lastStatus = lastStatus?.copy(
                    ledStripeStatus = LEDStripeStatus.onLow
                )
                onStatusUpdated(true)
            }
        }
    }

    fun switchLedStripeOnHigh(user: String?): Boolean? = asLocked {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) return@asLocked null

        sendCommand(LoRaPacketType.GARAGE_LIGHT_SET_DAC, ByteArray(4).apply {
            write(10000L.to16Bit(), 0)
            write(10000L.to16Bit(), 2)
        }, expectResponse = { it.type == LoRaPacketType.GARAGE_LIGHT_ACK }).apply {
            if (this) {
                lastStatus = lastStatus?.copy(
                    ledStripeStatus = LEDStripeStatus.onHigh
                )
                onStatusUpdated(true)
            }
        }
    }

    private fun sendCommand(
        type: LoRaPacketType,
        payload: ByteArray = byteArrayOf(),
        expectResponse: ((p: LoRaInboundPacketDecrypted) -> Boolean)?
    ): Boolean {
        val ct = CountDownLatch(1)
        var sent = false
        receiveQueue.clear()
        loRaConnection.send(
            keyId,
            loRaAddr,
            type,
            payload,
            null
        ) {
            ct.countDown()
            sent = it
        }
        ct.await(2, TimeUnit.SECONDS)

        if (!sent) {
            logger.warn { "Sending failed type=${type}" }
            return false
        }
        logger.info { "Sent $type payload=${payload.map { it.toUnsignedInt() }}" }

        return expectResponse?.let {
            receiveQueue.poll(2, TimeUnit.SECONDS)?.let {
                expectResponse(it)
            } ?: run {
                logger.info { "Got no reply - giving up" }
                false
            }
        } ?: true
    }

    private fun onStatusUpdated(changed: Boolean) {
        logger.debug { "New status=$lastStatus" }

        lastStatus?.let { status ->
            influxDBClient.recordSensorData(
                InfluxDBRecord(
                    Instant.ofEpochMilli(status.utcTimestampInMs),
                    "garageLightStatus",
                    mapOf(
                        "ceilinglight" to status.ceilingLightIsOn.toInt().toString(),
                        "ledStrip" to when (status.ledStripeStatus) {
                            LEDStripeStatus.off -> 0
                            LEDStripeStatus.onLow -> 1
                            LEDStripeStatus.onHigh -> 2
                        }.toString(),
                    ),
                    emptyMap()
                )
            )
        }


        if (changed) {
            listeners.forEach {
                try {
                    it.value(getCurrentState(SystemUser)!!)
                } catch (e: Exception) {
                    logger.error(e) { "" }
                }
            }
        }
    }

    override fun tickLocked(): Boolean {
        val status = lastStatus

        return if (status == null || clock.millis() - status.utcTimestampInMs > 25000) {

            var packet: LoRaInboundPacketDecrypted? = null
            val success = sendCommand(LoRaPacketType.GARAGE_LIGHT_GET_STATUS, expectResponse = {
                packet = it
                it.type == LoRaPacketType.GARAGE_LIGHT_STATUS_RESPONSE
            })

            if (success) {

                val milliVolts = readInt16Bits(packet!!.payload, 0)
                val newStatus = GarageLightStatus(
                    ceilingLightIsOn = packet!!.payload[4].toInt() != 0,
                    ledStripeStatus = when {
                        milliVolts == 0 -> LEDStripeStatus.off
                        milliVolts in (0..8000) -> LEDStripeStatus.onLow
                        else -> LEDStripeStatus.onHigh
                    },
                    timestampDelta = packet!!.timestampDelta,
                    utcTimestampInMs = clock.millis()
                )

                val changed = newStatus != lastStatus
                lastStatus = newStatus
                logger.info { "New garageLight status $lastStatus" }
                onStatusUpdated(changed)

                true
            } else {
                logger.info { "GARAGE_LIGHT_GET_STATUS not send" }
                false
            }
        } else true

    }


    override fun logger() = logger
}
