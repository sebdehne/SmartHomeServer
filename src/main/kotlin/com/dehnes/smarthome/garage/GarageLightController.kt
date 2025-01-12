package com.dehnes.smarthome.garage

import com.dehnes.smarthome.api.dtos.GarageLightStatus
import com.dehnes.smarthome.api.dtos.LEDStripeStatus
import com.dehnes.smarthome.config.ConfigService
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GarageLightController(
    executorService: ExecutorService,
    private val configService: ConfigService,
    private val influxDBClient: InfluxDBClient,
    private val userSettingsService: UserSettingsService,
) : AbstractProcess(executorService, 5) {

    private val logger = KotlinLogging.logger { }
    private val datagramSocket = DatagramSocket(9002)
    private var cmdAckHandler: (() -> Unit)? = null

    @Volatile
    private var lastInfluxDbRecordAt: Instant = Instant.now()


    private val listeners = ConcurrentHashMap<String, (GarageLightStatus) -> Unit>()

    @Volatile
    private var current: GarageLightStatus =
        GarageLightStatus(false, LEDStripeStatus.off, Instant.now().toEpochMilli())


    override fun logger() = logger

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

                        val msg = dst.parse()

                        when {
                            msg is CmdAck -> {
                                val ackHandlerCopy = cmdAckHandler
                                cmdAckHandler = null
                                ackHandlerCopy?.invoke()
                            }

                            msg is StatusResponse -> {
                                current = GarageLightStatus(
                                    msg.ceilingLight,
                                    msg.ledStripeStatus,
                                    msg.utcTimestampInMs,
                                )

                                if (msg.isNotify || lastInfluxDbRecordAt.plusSeconds(60).isBefore(Instant.now())) {
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
                                    lastInfluxDbRecordAt = Instant.now()
                                }

                                notifyListeners()
                            }
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

    private fun notifyListeners() {
        executorService.submit(withLogging {
            listeners.forEach { (_, fn) ->
                fn(current)
            }
        })
    }

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
        handleCmd(
            ByteArray(1) { MsgType.CMD_CEILING_LIGHT_ON.value.toByte() },
            user,
        ) {
            if (it) {
                current = current.copy(ceilingLightIsOn = true)
            }
            callback(it)
        }
    }

    fun switchOffCeilingLight(user: String?, callback: (r: Boolean) -> Unit) {
        handleCmd(
            ByteArray(1) { MsgType.CMD_CEILING_LIGHT_OFF.value.toByte() },
            user,
        ) {
            if (it) {
                current = current.copy(ceilingLightIsOn = false)
            }
            callback(it)
        }
    }

    fun switchLedStripeOff(user: String?, callback: (r: Boolean) -> Unit) {
        handleCmd(
            byteArrayOf(
                MsgType.CMD_LEDSTRIPE.value.toByte(),
                0,
                0,
                0,
                0,
            ),
            user,
        ) {
            if (it) {
                current = current.copy(ledStripeStatus = LEDStripeStatus.off)
            }
            callback(it)
        }
    }

    fun switchLedStripeOnLow(user: String?, callback: (r: Boolean) -> Unit) {
        val milliVolts = configService.getGarageSettings().lightsLedLowMilliVolts.toLong().to16Bit()
        handleCmd(
            byteArrayOf(
                MsgType.CMD_LEDSTRIPE.value.toByte(),
                milliVolts[0],
                milliVolts[1],
                milliVolts[0],
                milliVolts[1],
            ),
            user,
        ) {
            if (it) {
                current = current.copy(ledStripeStatus = LEDStripeStatus.onLow)
            }
            callback(it)
        }
    }

    fun switchLedStripeOnHigh(user: String?, callback: (r: Boolean) -> Unit) {
        val milliVolts = 10000L.to16Bit()
        handleCmd(
            byteArrayOf(
                MsgType.CMD_LEDSTRIPE.value.toByte(),
                milliVolts[0],
                milliVolts[1],
                milliVolts[0],
                milliVolts[1],
            ),
            user,
        ) {
            if (it) {
                current = current.copy(ledStripeStatus = LEDStripeStatus.onHigh)
            }
            callback(it)
        }
    }

    private fun handleCmd(sendBuf: ByteArray, user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }

        val c = CountDownLatch(1)
        val result = AtomicBoolean(false)
        cmdAckHandler = {
            result.set(true)
            c.countDown()
        }

        val garageSettings = configService.getGarageSettings()
        val req = DatagramPacket(
            sendBuf,
            0,
            sendBuf.size,
            InetAddress.getByName(garageSettings.lightsControllerIp),
            garageSettings.lightsControllerPort
        )
        datagramSocket.send(req)

        c.await(1, TimeUnit.SECONDS)

        callback(result.get())
        notifyListeners()
    }


    override fun tickLocked(): Boolean {
        val garageSettings = configService.getGarageSettings()

        val sendBuf = ByteArray(1) { MsgType.DATA_REQUEST.value.toByte() }
        val req = DatagramPacket(
            sendBuf,
            0,
            sendBuf.size,
            InetAddress.getByName(garageSettings.lightsControllerIp),
            garageSettings.lightsControllerPort
        )
        datagramSocket.send(req)

        return true
    }

    private fun ByteArray.parse(): Msg? = when (val type = this[0].toInt()) {
        1 -> { // DATA
            val ceilingLight = this[1].toInt() != 0
            val milliVoltsCh0 = readInt16Bits(this, 2)
            val milliVoltsCh1 = readInt16Bits(this, 4)
            val isNotify = this[6].toInt() != 0

            StatusResponse(
                ceilingLight = ceilingLight,
                ledStripeStatus = when {
                    milliVoltsCh0 == 0 -> LEDStripeStatus.off
                    milliVoltsCh0 in (1..8000) -> LEDStripeStatus.onLow
                    else -> LEDStripeStatus.onHigh
                },
                isNotify = isNotify,
                utcTimestampInMs = Instant.now().toEpochMilli(),
            )
        }

        5 -> CmdAck(utcTimestampInMs = Instant.now().toEpochMilli())
        else -> {
            logger.warn { "Unknown type=$type" }
            null
        }
    }

    sealed class Msg {
        abstract val utcTimestampInMs: Long
    }

    data class StatusResponse(
        val ceilingLight: Boolean,
        val ledStripeStatus: LEDStripeStatus,
        val isNotify: Boolean,
        override val utcTimestampInMs: Long,
    ) : Msg()

    data class CmdAck(
        override val utcTimestampInMs: Long,
    ) : Msg()

    enum class MsgType(val value: Int) {
        DATA_REQUEST(0),
        DATA(1),

        CMD_CEILING_LIGHT_ON(2),
        CMD_CEILING_LIGHT_OFF(3),
        CMD_LEDSTRIPE(4),
        CMD_ACK(5),
    }
}