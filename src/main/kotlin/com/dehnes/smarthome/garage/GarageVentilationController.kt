package com.dehnes.smarthome.garage

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.GarageSettings
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.readInt16Bits
import com.dehnes.smarthome.utils.to16Bit
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

class GarageVentilationController(
    private val configService: ConfigService,
    private val userSettingsService: UserSettingsService,
    executorService: ExecutorService,
) : AbstractProcess(executorService, 5) {
    private val logger = KotlinLogging.logger {}
    private var datagramSocket: DatagramSocket? = null

    @Volatile
    private var toBeSent: Pair<Int, (r: Boolean) -> Unit>? = null

    @Volatile
    private var current: GarageVentilationState = GarageVentilationState(0)

    override fun logger() = logger

    private val listeners = ConcurrentHashMap<String, (GarageVentilationState) -> Unit>()
    fun addListener(user: String?, id: String, l: (GarageVentilationState) -> Unit) {
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

    fun send(milliVolts: Int, callback: (r: Boolean) -> Unit) {
        toBeSent = milliVolts to callback
        tick()
    }

    fun getCurrent() = current

    override fun tickLocked(): Boolean {
        val garageSettings = configService.getGarageSettings()

        val toBeSentCopy = toBeSent ?: (current.milliVolts to { r: Boolean -> })
        toBeSent = null

        val resp = tx(
            cmd = toBeSentCopy.first,
            garageSettings = garageSettings,
            timeout = Duration.ofMillis(1000)
        )

        val wasSuccess = resp?.first == true
        toBeSentCopy.second(wasSuccess)

        if (resp != null) {
            current = resp.second

            executorService.submit(withLogging {
                listeners.forEach { (_, fn) ->
                    fn(current)
                }
            })
        }

        return true
    }

    private fun tx(
        cmd: Int,
        garageSettings: GarageSettings,
        timeout: Duration
    ): Pair<Boolean, GarageVentilationState>? {
        if (datagramSocket == null) {
            datagramSocket = DatagramSocket(9001)
            datagramSocket!!.soTimeout = timeout.toMillis().toInt()
        }

        val req = DatagramPacket(
            cmd.toLong().to16Bit(),
            0,
            2,
            InetAddress.getByName(garageSettings.ventilationBridgeIp),
            garageSettings.ventilationBridgePort
        )
        datagramSocket!!.send(req)

        // receive response
        val buf = ByteArray(2) { 0 }
        return try {
            datagramSocket!!.receive(DatagramPacket(buf, 0, buf.size))

            true to GarageVentilationState(readInt16Bits(buf, 0))
        } catch (_: SocketTimeoutException) {
            null
        }
    }
}

data class GarageVentilationState(
    val milliVolts: Int,
    val createdAt: Instant = Instant.now(),
)