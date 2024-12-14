package com.dehnes.smarthome.garage

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.GarageSettings
import com.dehnes.smarthome.users.SystemUser
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.readLong32Bits
import com.dehnes.smarthome.utils.toUnsignedInt
import com.dehnes.smarthome.utils.withLogging
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit


class HoermannE4Controller(
    private val configService: ConfigService,
    private val userSettingsService: UserSettingsService,
    private val garageController: GarageController,
    executorService: ExecutorService,
) : AbstractProcess(executorService, 1) {
    private val logger = KotlinLogging.logger {}
    private var datagramSocket: DatagramSocket? = null

    @Volatile
    private var toBeSent: Pair<HoermannE4Command, (r: Boolean) -> Unit>? = null

    @Volatile
    private var current: HoermannE4Broadcast = HoermannE4Broadcast(
        targetPos = 0,
        currentPos = 0,
        doorState = SupramatiDoorState.CLOSED,
        isVented = false,
        motorSpeed = 0,
        light = false,
        motorRunning = false,
        ageInMs = 0,
    )

    private val listeners = ConcurrentHashMap<String, (HoermannE4Broadcast) -> Unit>()
    fun addListener(user: String?, id: String, l: (HoermannE4Broadcast) -> Unit) {
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

    fun send(cmd: HoermannE4Command, callback: (r: Boolean) -> Unit) {
        toBeSent = cmd to callback
        tick()
    }

    fun getCurrent() = current

    override fun logger() = logger

    override fun tickLocked(): Boolean {
        val garageSettings = configService.getGarageSettings()

        val toBeSentCopy = toBeSent ?: (HoermannE4Command.Nop to { r: Boolean -> })
        toBeSent = null

        val resp = tx(
            cmd = if (configService.isDevMode()) HoermannE4Command.Nop else toBeSentCopy.first,
            garageSettings = garageSettings,
            timeout = Duration.ofMillis(1000)
        )

        val wasSuccess = resp?.first == true
        toBeSentCopy.second(wasSuccess)

        if (resp != null) {
            if (toBeSentCopy.first == HoermannE4Command.Open && wasSuccess) {
                garageController.switchOnCeilingLight(SystemUser)
            } else if (toBeSentCopy.first == HoermannE4Command.Close && wasSuccess) {
                timer.schedule({
                    executorService.submit(withLogging {
                        garageController.switchOffCeilingLight(SystemUser)
                    })
                }, garageSettings.lightOffAfterCloseDelaySeconds, TimeUnit.SECONDS)
            }

            current = resp.second

            executorService.submit(withLogging {
                listeners.forEach { (_, fn) ->
                    fn(current)
                }
            })
        }

        return true
    }

    private fun tx(cmd: HoermannE4Command, garageSettings: GarageSettings, timeout: Duration): Pair<Boolean, HoermannE4Broadcast>? {
        if (datagramSocket == null) {
            datagramSocket = DatagramSocket(9000)
            datagramSocket!!.soTimeout = timeout.toMillis().toInt()
        }

        val sendBuf = byteArrayOf(0)
        sendBuf[0] = cmd.value.toByte()
        val req = DatagramPacket(
            sendBuf,
            0,
            sendBuf.size,
            InetAddress.getByName(garageSettings.hoermannBridgeIp),
            garageSettings.hoermannBridgePort
        )
        datagramSocket!!.send(req)

        // receive response
        val buf = ByteArray(12) { 0 }
        return try {
            datagramSocket!!.receive(DatagramPacket(buf, 0, buf.size))

            val sendResult = buf[0].toInt() == 1

            sendResult to HoermannE4Broadcast(
                targetPos = buf[1].toUnsignedInt(),
                currentPos = buf[2].toUnsignedInt(),
                doorState = SupramatiDoorState.entries.first { it.value == buf[3].toInt() },
                isVented = buf[4].toInt() != 0,
                motorSpeed = buf[5].toUnsignedInt(),
                light = buf[6].toInt() != 0,
                motorRunning = buf[7].toInt() != 0,
                ageInMs = readLong32Bits(buf, 8)
            )
        } catch (_: SocketTimeoutException) {
            null
        }
    }


}


enum class HoermannE4Command(val value: Int) {
    Nop(0),
    Open(1),
    Close(2),
    Toggle(3),
    Light(4),
    Vent(5),
    HalvOpen(6),
}

data class HoermannE4Broadcast(
    //val count: Int,
    val targetPos: Int,
    val currentPos: Int,
    val doorState: SupramatiDoorState,
    val isVented: Boolean,
    val motorSpeed: Int,
    val light: Boolean,
    val motorRunning: Boolean,
    val ageInMs: Long,
    val receivedAt: Instant = Instant.now()
) {
    fun withoutTime() = copy(
        ageInMs = 0,
        receivedAt = Instant.ofEpochMilli(0)
    )
}

enum class SupramatiDoorState(val value: Int) {
    STOPPED(0x00),

    OPENING(0x01),
    CLOSING(0x02),
    HALV_OPENING(0x05),
    VENTING(0x09),

    OPEN(0x20),
    CLOSED(0x40),
    HALV_OPEN(0x80),
}
