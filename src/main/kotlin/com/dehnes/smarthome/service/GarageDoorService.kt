package com.dehnes.smarthome.service

import com.dehnes.smarthome.api.DoorStatus
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
    private val defaultAutoCloseInSeconds = 60 * 30

    // guared by "this"
    private var lastStatus: GarageStatus? = null

    @Synchronized
    fun updateAutoCloseAfter(autoCloseDeltaInSeconds: Long) {
        val garageStatus = lastStatus
        if (garageStatus?.autoCloseAfter != null) {
            lastStatus = GarageStatus(
                garageStatus.lightIsOn,
                garageStatus.doorStatus,
                garageStatus.autoCloseAfter + (autoCloseDeltaInSeconds * 1000)
            )
        }
    }

    @Synchronized
    fun sendCommand(doorCommandOpen: Boolean): Boolean {
        logger.info { "sendCommand doorCommandOpen=$doorCommandOpen current=$lastStatus" }

        val autoCloseAfter: Long?
        val sent: Boolean
        val newStatus = if (doorCommandOpen) {
            sent = sendCommandInternal(true)
            autoCloseAfter = System.currentTimeMillis() + (defaultAutoCloseInSeconds * 1000)
            DoorStatus.doorOpening
        } else {
            sent = sendCommandInternal(false)
            autoCloseAfter = null
            DoorStatus.doorClosing
        }

        lastStatus = GarageStatus(
            lastStatus?.lightIsOn ?: false,
            newStatus,
            autoCloseAfter
        )
        onStatusChanged()

        return sent
    }

    @Synchronized
    fun handleIncoming(statusReceived: RfPacket) {
        if (statusReceived.remoteAddr != rfAddr) {
            return
        }

        Thread.sleep(500)

        logger.info { "handleIncoming statusReceived=$statusReceived current=$lastStatus" }

        var newStatus = if (statusReceived.isDoorOpen()) DoorStatus.doorOpen else DoorStatus.doorClosed
        var autoCloseAfter = lastStatus?.autoCloseAfter
        if (lastStatus != null) {
            val current = lastStatus!!
            when {
                current.doorStatus == DoorStatus.doorClosed && statusReceived.isDoorClosed() -> {
                }
                current.doorStatus == DoorStatus.doorClosed && statusReceived.isDoorOpen() -> {
                    // open with another remote? accept
                    autoCloseAfter = System.currentTimeMillis() + (defaultAutoCloseInSeconds * 1000)
                }
                current.doorStatus == DoorStatus.doorOpen && statusReceived.isDoorClosed() -> {
                    // closed with another remote? accept
                    autoCloseAfter = null
                }
                current.doorStatus == DoorStatus.doorOpen && statusReceived.isDoorOpen() -> {
                    if (autoCloseAfter != null && System.currentTimeMillis() > autoCloseAfter) {
                        // close now
                        sendCommandInternal(false)
                        newStatus = DoorStatus.doorClosing
                        autoCloseAfter = null
                    } else if (autoCloseAfter == null) {
                        autoCloseAfter = System.currentTimeMillis() + (defaultAutoCloseInSeconds * 1000)
                    }
                }
                current.doorStatus == DoorStatus.doorOpening && statusReceived.isDoorClosed() -> {
                    // OK - do not implement auto-retry for open - giveup
                    autoCloseAfter = null
                }
                current.doorStatus == DoorStatus.doorOpening && statusReceived.isDoorOpen() -> {
                    // OK - reach desired state
                }
                current.doorStatus == DoorStatus.doorClosing && statusReceived.isDoorClosed() -> {
                    // OK - reach desired state
                }
                current.doorStatus == DoorStatus.doorClosing && statusReceived.isDoorOpen() -> {
                    // OK - retry
                    sendCommandInternal(false)
                    newStatus = DoorStatus.doorClosing
                }
            }
        }

        lastStatus = GarageStatus(
            statusReceived.isLightOn(),
            newStatus,
            autoCloseAfter
        )

        onStatusChanged()
    }

    @Synchronized
    fun getCurrentState() = lastStatus

    private fun onStatusChanged() {

        logger.info { "New status=$lastStatus" }

        influxDBClient.recordSensorData(
            "garageStatus",
            listOf(
                "light" to lastStatus!!.lightIsOn.toInt().toString(),
                "door" to lastStatus!!.doorStatus.influxDbValue.toString()
            )
        )

        listeners.forEach {
            try {
                it.value(lastStatus!!)
            } catch (e: Exception) {
                logger.error("", e)
            }
        }
    }

    private fun sendCommandInternal(openCommand: Boolean) = try {
        serialConnection.send(
            RfPacket(
                rfAddr, intArrayOf(if (openCommand) 1 else 2)
            )
        )
        true
    } catch (e: Exception) {
        logger.warn("Could not send Close command", e)
        false
    }

    private fun RfPacket.isDoorOpen(): Boolean {
        /*
         * ch3 door
         * >100 : door is not closed
         * <100 : door is closed
         */
        val ch3: Int = merge(this.message[4], this.message[5])
        return ch3 > 100
    }

    private fun RfPacket.isDoorClosed() = !this.isDoorOpen()

    private fun RfPacket.isLightOn(): Boolean {
        /*
         * ch1 light
         * 0  : ON
         * >0 : OFF
         */
        val ch1: Int = merge(this.message[0], this.message[1])
        return ch1 <= 10
    }

}

fun Boolean.toInt() = if (this) 1 else 0
