package com.dehnes.smarthome.garage

import com.dehnes.smarthome.api.dtos.GarageLightStatus
import com.dehnes.smarthome.api.dtos.LEDStripeStatus
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.GarageSettings
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class GarageLightController(
    private val configService: ConfigService,
    private val influxDBClient: InfluxDBClient,
    executorService: ExecutorService,
    private val userSettingsService: UserSettingsService,
) : AbstractProcess(executorService, 5) {

    @Volatile
    private var lastInfluxDbRecordAt: Instant = Instant.now()

    @Volatile
    private var toBeSent: Pair<GarageLightRequest, (r: Boolean) -> Unit>? = null

    @Volatile
    private var receiveHandler: ((buf: ByteArray) -> Unit)? = null

    private val datagramSocket = DatagramSocket(9002)

    @Volatile
    private var current: GarageLightStatus =
        GarageLightStatus(false, LEDStripeStatus.off, Instant.now().toEpochMilli())

    private val logger = KotlinLogging.logger { }

    override fun onStart() {
        Thread {
            try {
                val buf = ByteArray(100)
                while (true) {
                    try {
                        val datagramPacket = DatagramPacket(buf, 0, buf.size)
                        datagramSocket.receive(datagramPacket)
                        val dst = ByteArray(datagramPacket.length) { 0 }
                        System.arraycopy(buf, 0, dst, 0, dst.size)

                        val receiveHandlerCopy = receiveHandler
                        receiveHandler = null

                        receiveHandlerCopy?.let { h ->
                            logger.info { "Handling with receiveHandler (response)" }
                            h(dst)
                        } ?: run {
                            logger.info { "Handling as notify" }
                            // treat as NOTIFY
                            current = dst.parse()
                            // send ACK
                            logger.info { "Sending ACK" }
                            val garageSettings = configService.getGarageSettings()
                            datagramSocket.send(
                                DatagramPacket(
                                    byteArrayOf(0),
                                    0,
                                    1,
                                    InetAddress.getByName(garageSettings.lightsControllerIp),
                                    garageSettings.lightsControllerPort
                                )
                            )
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "Exception while receiving" }
                    }
                }
            } finally {
                datagramSocket.close()
                logger.error { "closing DatagramSocket" }
            }
        }.apply {
            isDaemon = true
            name = "garage-light-service"
        }.start()
    }

    private val listeners = ConcurrentHashMap<String, (GarageLightStatus) -> Unit>()

    fun addListener(user: String?, id: String, l: (GarageLightStatus) -> Unit) {
        check(
            userSettingsService.canUserRead(
                user,
                UserRole.garageDoor
            )
        ) { "User $user cannot read garage door" }
        listeners[id] = l
    }

    fun removeListener(id: String) {
        listeners.remove(id)
    }

    fun getCurrentState(user: String?): GarageLightStatus? {
        if (!userSettingsService.canUserRead(user, UserRole.garageDoor)) return null
        return current
    }

    fun switchOnCeilingLight(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }

        toBeSent = GarageLightRequest(true, current.ledStripeStatus) to callback
        tick()
    }

    fun switchOffCeilingLight(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }

        toBeSent = GarageLightRequest(false, current.ledStripeStatus) to callback
        tick()
    }

    fun switchLedStripeOff(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }

        toBeSent = GarageLightRequest(current.ceilingLightIsOn, LEDStripeStatus.off) to callback
        tick()
    }

    fun switchLedStripeOnLow(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }

        toBeSent = GarageLightRequest(current.ceilingLightIsOn, LEDStripeStatus.onLow) to callback
        tick()
    }

    fun switchLedStripeOnHigh(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }

        toBeSent = GarageLightRequest(current.ceilingLightIsOn, LEDStripeStatus.onHigh) to callback
        tick()
    }

    override fun tickLocked(): Boolean {
        val (request, callback) = toBeSent ?: (GarageLightRequest(
            current.ceilingLightIsOn,
            current.ledStripeStatus,
        ) to { r: Boolean -> })
        toBeSent = null

        receiveHandler = {
            logger.info { "in tickLocked() receiveHandler" }
            val resp = it.parse()
            callback(true)

            val change = resp.ceilingLightIsOn != current.ceilingLightIsOn
            current = resp

            if (change || lastInfluxDbRecordAt.plusSeconds(60).isBefore(Instant.now())) {
                lastInfluxDbRecordAt = Instant.now()
                influxDBClient.recordSensorData(
                    InfluxDBRecord(
                        Instant.ofEpochMilli(current.utcTimestampInMs),
                        "garageLightStatus",
                        mapOf(
                            "ceilinglight" to current.ceilingLightIsOn.toInt().toString(),
                            "ledStrip" to when (current.ledStripeStatus) {
                                LEDStripeStatus.off -> 0
                                LEDStripeStatus.onLow -> 1
                                LEDStripeStatus.onHigh -> 2
                            }.toString(),
                        ),
                        emptyMap()
                    )
                )
            }

            executorService.submit(withLogging {
                listeners.forEach { (_, fn) ->
                    fn(current)
                }
            })
        }

        tx(request, configService.getGarageSettings())

        return true
    }

    private fun tx(
        cmd: GarageLightRequest,
        garageSettings: GarageSettings,
    ) {

        val sendBuf = ByteArray(5) { 0 }
        sendBuf[0] = cmd.ceilingLight.toInt().toByte()
        when (cmd.ledStripeStatus) {
            LEDStripeStatus.off -> 0
            LEDStripeStatus.onLow -> garageSettings.lightsLedLowMilliVolts
            LEDStripeStatus.onHigh -> 10000
        }.toLong().to16Bit().let {
            sendBuf[1] = it[0]
            sendBuf[2] = it[0]
            sendBuf[3] = it[0]
            sendBuf[4] = it[0]
        }

        val req = DatagramPacket(
            sendBuf,
            0,
            sendBuf.size,
            InetAddress.getByName(garageSettings.lightsControllerIp),
            garageSettings.lightsControllerPort
        )
        datagramSocket.send(req)
    }

    private fun ByteArray.parse(): GarageLightStatus {
        val milliVoltsCh0 = readInt16Bits(this, 1)
        val milliVoltsCh1 = readInt16Bits(this, 3)

        return GarageLightStatus(
            ceilingLightIsOn = this[0].toInt() != 0,
            ledStripeStatus = when {
                milliVoltsCh0 == 0 -> LEDStripeStatus.off
                milliVoltsCh0 in (1..8000) -> LEDStripeStatus.onLow
                else -> LEDStripeStatus.onHigh
            },
            utcTimestampInMs = Instant.now().toEpochMilli()
        )
    }

    override fun logger() = logger

    data class GarageLightRequest(
        val ceilingLight: Boolean,
        val ledStripeStatus: LEDStripeStatus,
    )
}
