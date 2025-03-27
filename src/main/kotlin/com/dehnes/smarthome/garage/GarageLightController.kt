package com.dehnes.smarthome.garage

import com.dehnes.smarthome.api.dtos.GarageLightResponse
import com.dehnes.smarthome.api.dtos.GarageLightStatus
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.LEDStripeStatus
import com.dehnes.smarthome.config.LightLedMode
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.daylight.DayLightService
import com.dehnes.smarthome.daylight.DayLightSunState
import com.dehnes.smarthome.users.SystemUser
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

val delayInSeconds = 5L

class GarageLightController(
    executorService: ExecutorService,
    private val configService: ConfigService,
    private val influxDBClient: InfluxDBClient,
    private val userSettingsService: UserSettingsService,
    private val dayLightService: DayLightService,
    private val hoermannE4Controller: HoermannE4Controller,
) : AbstractProcess(executorService, delayInSeconds) {

    private val logger = KotlinLogging.logger { }
    private val datagramSocket = DatagramSocket(9002)
    private var cmdAckHandler: (() -> Unit)? = null

    private var lastPingSent = Instant.now().minusSeconds(10)

    @Volatile
    private var lastReceivedGarageLightStatus: StatusResponse? = null

    @Volatile
    private var callBacks = CopyOnWriteArrayList<(r: Boolean) -> Unit>()

    @Volatile
    private var lastInfluxDbRecordAt: Instant = Instant.now()

    @Volatile
    private var autoCeilingLightOffAt: Instant? = null

    private val listeners = ConcurrentHashMap<String, (GarageLightStatus) -> Unit>()

    override fun logger() = logger

    override fun onStart() {
        dayLightService.listeners[this::class.qualifiedName!!] = { tick() }
        hoermannE4Controller.addListener(SystemUser, this::class.qualifiedName!!) { tick() }

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
                                lastReceivedGarageLightStatus = msg

                                if (msg.isNotify || lastInfluxDbRecordAt.plusSeconds(60).isBefore(Instant.now())) {
                                    influxDBClient.recordSensorData(
                                        InfluxDBRecord(
                                            Instant.ofEpochMilli(lastReceivedGarageLightStatus!!.utcTimestampInMs),
                                            "garageLightStatus",
                                            mapOf(
                                                "ceilinglight" to lastReceivedGarageLightStatus!!.ceilingLight.toInt()
                                                    .toString(),
                                                "ledStrip" to when (lastReceivedGarageLightStatus!!.ledStripeStatus) {
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

                                tick()
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
        val garageSettings = configService.getGarageSettings()
        return GarageLightStatus(
            ceilingLightIsOn = lastReceivedGarageLightStatus?.ceilingLight == true,
            ledStripeStatus = garageSettings.currentLEDStripeStatus,
            utcTimestampInMs = lastReceivedGarageLightStatus?.utcTimestampInMs ?: Instant.now().toEpochMilli(),
            ledStripeLowMillivolts = garageSettings.lightsLedLowMilliVolts,
            ledStripeCurrentMode = garageSettings.currentLEDStripeMode,
        )
    }

    fun setLedStripeLowMillivolts(user: String?, ledStripeLowMillivolts: Int): GarageLightResponse {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            return GarageLightResponse(commandSendSuccess = false)
        }
        check(ledStripeLowMillivolts in 1000..9000)
        configService.setGarageSettings {
            it.copy(
                lightsLedLowMilliVolts = ledStripeLowMillivolts
            )
        }
        tick()
        return GarageLightResponse(commandSendSuccess = true, status = getCurrentState(user))
    }

    fun setLedStripeMode(user: String?, mode: LightLedMode): GarageLightResponse {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            return GarageLightResponse(commandSendSuccess = false)
        }
        configService.setGarageSettings {
            it.copy(
                currentLEDStripeMode = mode
            )
        }
        tick()
        return GarageLightResponse(commandSendSuccess = true, status = getCurrentState(user))
    }

    fun switchOnCeilingLight(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }
        handleCmd(
            ByteArray(1) { MsgType.CMD_CEILING_LIGHT_ON.value.toByte() },
        ) {
            if (it) {
                lastReceivedGarageLightStatus = lastReceivedGarageLightStatus?.copy(
                    ceilingLight = true
                )
            }
            callback(it)
        }
    }

    fun switchOffCeilingLight(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }
        handleCmd(
            ByteArray(1) { MsgType.CMD_CEILING_LIGHT_OFF.value.toByte() },
        ) {
            if (it) {
                lastReceivedGarageLightStatus = lastReceivedGarageLightStatus?.copy(
                    ceilingLight = false
                )
            }
            callback(it)
        }
    }

    fun switchLedStripeOff(user: String?, callback: (r: Boolean) -> Unit) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }
        configService.setGarageSettings {
            it.copy(currentLEDStripeStatus = LEDStripeStatus.off)
        }
        callBacks.add(callback)
        tick()
    }

    private fun switchLedStripeOffPrivate(callback: (r: Boolean) -> Unit) {
        handleCmd(
            byteArrayOf(
                MsgType.CMD_LEDSTRIPE.value.toByte(),
                0,
                0,
                0,
                0,
            ),
            {
                if (it) {
                    lastReceivedGarageLightStatus = lastReceivedGarageLightStatus?.copy(
                        ledStripeStatus = LEDStripeStatus.off
                    )
                }
                callback(it)
            }
        )
    }

    fun switchLedStripeOnLow(user: String?, callback: (r: Boolean) -> Unit = {}) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }
        configService.setGarageSettings {
            it.copy(
                currentLEDStripeStatus = LEDStripeStatus.onLow,
            )
        }
        callBacks.add(callback)
        tick()
    }

    private fun switchLedStripeOnLowPrivate(callback: (r: Boolean) -> Unit) {
        configService.setGarageSettings {
            it.copy(
                currentLEDStripeStatus = LEDStripeStatus.onLow,
            )
        }
        val milliVolts = configService.getGarageSettings().lightsLedLowMilliVolts.toLong().to16Bit()
        handleCmd(
            byteArrayOf(
                MsgType.CMD_LEDSTRIPE.value.toByte(),
                milliVolts[0],
                milliVolts[1],
                milliVolts[0],
                milliVolts[1],
            ),
            {
                if (it) {
                    lastReceivedGarageLightStatus = lastReceivedGarageLightStatus?.copy(
                        ledStripeStatus = LEDStripeStatus.onLow
                    )
                }
                callback(it)
            }
        )
    }

    fun switchLedStripeOnHigh(user: String?, callback: (r: Boolean) -> Unit = {}) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            callback(false)
            return
        }
        configService.setGarageSettings {
            it.copy(
                currentLEDStripeStatus = LEDStripeStatus.onHigh,
            )
        }
        callBacks.add(callback)
        tick()
    }

    private fun switchLedStripeOnHighPrivate(callback: (r: Boolean) -> Unit) {
        configService.setGarageSettings {
            it.copy(
                currentLEDStripeStatus = LEDStripeStatus.onHigh,
            )
        }
        val milliVolts = 10000L.to16Bit()
        handleCmd(
            byteArrayOf(
                MsgType.CMD_LEDSTRIPE.value.toByte(),
                milliVolts[0],
                milliVolts[1],
                milliVolts[0],
                milliVolts[1],
            ),
            {
                if (it) {
                    lastReceivedGarageLightStatus = lastReceivedGarageLightStatus?.copy(
                        ledStripeStatus = LEDStripeStatus.onHigh
                    )
                }
                callback(it)
            }
        )
    }

    private fun handleCmd(sendBuf: ByteArray, callback: (r: Boolean) -> Unit = {}) {
        if (configService.isDevMode()) {
            callback(true)
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
        val doorState = hoermannE4Controller.getCurrent().doorState

        /*
         * Handle ceiling light
         */
        val garageDoorOpening = doorState in listOf(
            SupramatiDoorState.HALV_OPENING,
            SupramatiDoorState.OPENING
        )
        val garageDoorClosing = doorState in listOf(
            SupramatiDoorState.CLOSING,
        )
        when {
            garageDoorOpening && lastReceivedGarageLightStatus?.ceilingLight == false -> switchOnCeilingLight(SystemUser) {}
            garageDoorClosing -> {
                autoCeilingLightOffAt = Instant.now().plusSeconds(garageSettings.lightOffAfterCloseDelaySeconds)
            }
        }

        autoCeilingLightOffAt?.let {
            if (Instant.now().isAfter(it)) {
                logger.info { "Auto ceilinglight off" }
                autoCeilingLightOffAt = null
                switchOffCeilingLight(SystemUser) {}
            }
        }

        /*
         * Handle LED-stripe
         */
        if (garageSettings.currentLEDStripeMode == LightLedMode.auto) {
            val dayLightSunState = dayLightService.getStatus()
            var target = when {
                dayLightSunState == DayLightSunState.up -> LEDStripeStatus.off
                doorState != SupramatiDoorState.CLOSED -> LEDStripeStatus.onHigh
                else -> LEDStripeStatus.onLow
            }
            if (garageSettings.currentLEDStripeStatus != target) {
                logger.info { "Setting LED Stripe target=$target" }
                configService.setGarageSettings {
                    it.copy(currentLEDStripeStatus = target)
                }
            }
        }

        lastReceivedGarageLightStatus?.apply {
            val updatedSettings = configService.getGarageSettings()
            if (ledStripeStatus != updatedSettings.currentLEDStripeStatus) {
                val callback = { result: Boolean ->
                    val l = callBacks
                    callBacks = CopyOnWriteArrayList()
                    l.forEach { it.invoke(result) }
                }
                logger.info { "Bringing LED Stripe to configured target=${updatedSettings.currentLEDStripeStatus}" }
                when (updatedSettings.currentLEDStripeStatus) {
                    LEDStripeStatus.onLow -> switchLedStripeOnLowPrivate(callback)
                    LEDStripeStatus.onHigh -> switchLedStripeOnHighPrivate(callback)
                    LEDStripeStatus.off -> switchLedStripeOffPrivate(callback)
                }
            }
        }

        if (!configService.isDevMode() && lastPingSent.plusSeconds(delayInSeconds).isBefore(Instant.now())) {
            val sendBuf = ByteArray(1) { MsgType.DATA_REQUEST.value.toByte() }
            val req = DatagramPacket(
                sendBuf,
                0,
                sendBuf.size,
                InetAddress.getByName(garageSettings.lightsControllerIp),
                garageSettings.lightsControllerPort
            )
            datagramSocket.send(req)
            lastPingSent = Instant.now()
        }

        return true
    }

    private fun notifyListeners() {
        val garageLightStatus = getCurrentState(SystemUser) ?: return
        executorService.submit(withLogging {
            listeners.forEach { (_, fn) ->
                fn(garageLightStatus)
            }
        })
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
                    milliVoltsCh0 > 9500 -> LEDStripeStatus.onHigh
                    else -> LEDStripeStatus.onLow
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