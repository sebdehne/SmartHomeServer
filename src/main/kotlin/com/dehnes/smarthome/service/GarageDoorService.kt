package com.dehnes.smarthome.service

import com.dehnes.smarthome.api.GarageStatus
import com.dehnes.smarthome.external.InfluxDBClient
import com.dehnes.smarthome.external.RfPacket
import com.dehnes.smarthome.external.SerialConnection
import com.dehnes.smarthome.math.merge
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

class GarageDoorService(
    private val serialConnection: SerialConnection,
    private val influxDBClient: InfluxDBClient
) {
    private val logger = KotlinLogging.logger { }

    val listeners = ConcurrentHashMap<String, (GarageStatus) -> Unit>()

    private val rfAddr = 24

    @Volatile
    private var lastStatus: GarageStatus? = null

    private val dbType = "garage"

    fun sendOpenCommand() = try {
        serialConnection.send(RfPacket(rfAddr, intArrayOf(1)))
        true
    } catch (e: Exception) {
        logger.warn("Could not send Open command", e)
        false
    }

    fun sendCloseCommand() = try {
        serialConnection.send(RfPacket(rfAddr, intArrayOf(2)))
        true
    } catch (e: Exception) {
        logger.warn("Could not send Close command", e)
        false
    }

    fun handleIncoming(rfPacket: RfPacket) {
        if (rfPacket.remoteAddr != rfAddr) {
            return
        }

        /*
         * ch1 light
         * 0  : ON
         * >0 : OFF
         *
         * ch2 broken
         *
         * ch3
         * >100 : door is not closed
         * <100 : door is closed
         */
        val ch1: Int = merge(rfPacket.message[0], rfPacket.message[1])
        //int ch2 = ByteTools.merge(msg[2], msg[3]);
        val ch3: Int = merge(rfPacket.message[4], rfPacket.message[5])

        var lightIsOn = true
        if (ch1 > 10) {
            lightIsOn = false
        }

        var doorIsOpen = false
        if (ch3 > 100) {
            doorIsOpen = true
        }

        logger.info("Garage door. Light=$lightIsOn, Door=$doorIsOpen")

        influxDBClient.recordSensorData(
            dbType, listOf(
                "light" to lightIsOn.toString(),
                "door" to doorIsOpen.toString()
            )
        )

        lastStatus = GarageStatus(lightIsOn, doorIsOpen)

        listeners.forEach {
            try {
                it.value(lastStatus!!)
            } catch (e: Exception) {
                logger.error("", e)
            }
        }
    }

    fun getCurrentState() = lastStatus

}

