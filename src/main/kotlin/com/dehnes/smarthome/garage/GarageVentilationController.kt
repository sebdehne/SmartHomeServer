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
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class GarageVentilationController(
    private val configService: ConfigService,
    private val userSettingsService: UserSettingsService,
    executorService: ExecutorService,
) : AbstractProcess(executorService, 10) {
    private val logger = KotlinLogging.logger {}
    private var datagramSocket: DatagramSocket? = null

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

    fun setMilliVolts(user: String?, milliVolts: Int) {
        if (!userSettingsService.canUserWrite(user, UserRole.garageDoor)) {
            return
        }

        check(milliVolts in 0..10000)

        configService.setGarageSettings {
            it.copy(ventilationMilliVolts = milliVolts)
        }

        tick()
    }

    fun getCurrent(user: String?): GarageVentilationState? {
        if (!userSettingsService.canUserRead(user, UserRole.garageDoor)) return null

        return current
    }

    override fun tickLocked(): Boolean {
        val garageSettings = configService.getGarageSettings()

        val resp = tx(
            cmd = garageSettings.ventilationMilliVolts,
            garageSettings = garageSettings,
        )

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
    ): Pair<Boolean, GarageVentilationState>? {
        if (datagramSocket == null) {
            datagramSocket = DatagramSocket(9001)
            datagramSocket!!.soTimeout = garageSettings.soTimeout
        }

        val req = DatagramPacket(
            cmd.toLong().to16Bit(),
            0,
            2,
            InetAddress.getByName(garageSettings.ventilationBridgeIp),
            garageSettings.ventilationBridgePort
        )
        datagramSocket!!.send(req)
        logger.info { "Sendt request to ventilation milliVolts=$cmd" }

        // receive response
        val buf = ByteArray(2) { 0 }
        return try {
            datagramSocket!!.receive(DatagramPacket(buf, 0, buf.size))

            val milliVolts = readInt16Bits(buf, 0)
            logger.info { "Received response from ventilation milliVolts=$milliVolts" }
            true to GarageVentilationState(milliVolts)
        } catch (_: SocketTimeoutException) {
            null
        }
    }
}

data class GarageVentilationState(
    val milliVolts: Int,
    val createdAt: Instant = Instant.now(),
)