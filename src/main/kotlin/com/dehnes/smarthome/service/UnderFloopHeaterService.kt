package com.dehnes.smarthome.service

import com.dehnes.smarthome.external.InfluxDBClient
import com.dehnes.smarthome.external.RfPacket
import com.dehnes.smarthome.external.SerialConnection
import com.dehnes.smarthome.math.Sht15SensorService
import com.dehnes.smarthome.math.Sht15SensorService.getRelativeHumidity
import com.dehnes.smarthome.math.divideBy100
import mu.KotlinLogging
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

private const val TARGET_TEMP_KEY = "HeatingControllerService.targetTemp"
private const val HEATER_STATUS_KEY = "HeatingControllerService.heaterTarget"
private const val OPERATING_MODE = "HeatingControllerService.operatingMode"

class UnderFloopHeaterService(
    private val serialConnection: SerialConnection,
    private val executorService: ExecutorService,
    private val persistenceService: PersistenceService,
    private val influxDBClient: InfluxDBClient,
    private val tibberService: TibberService
) {

    private val rfAddr = 27
    private val commandReadStatus = 1
    private val commandSwitchOnHeater = 2
    private val commandSwitchOffHeater = 3

    private val logger = KotlinLogging.logger { }
    private val runLock = ReentrantLock()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val failedAttempts = AtomicInteger(0)

    private var lastSwitchedTimestamp: Long = 0
    private var msgListener: ((RfPacket) -> Unit)? = null

    fun start() {
        timer.scheduleAtFixedRate({
            executorService.submit {
                if (runLock.tryLock()) {
                    try {
                        tick()
                    } catch (e: Exception) {
                        logger.error("", e)
                    } finally {
                        runLock.unlock()
                    }
                }
            }
        }, 2, 2, TimeUnit.MINUTES)
    }

    private fun tick(): Boolean {
        val currentMode: Mode = getCurrentMode()
        logger.info("Current mode: $currentMode")

        // request measurement
        val rfPacket: RfPacket? = executeCommand(commandReadStatus)
        // TODO ignore values too unrealistic / remember last values in memory -
        recordLocalValues(currentMode, failedAttempts.get())
        val temperatureAndHeaterStatus = rfPacket?.let { recordRfValues(it) } ?: return false


        // evaluate state
        when (currentMode) {
            Mode.OFF -> persistenceService[HEATER_STATUS_KEY] = "off"
            Mode.ON -> persistenceService[HEATER_STATUS_KEY] = "on"
            Mode.MANUAL -> {
                val targetTemperature = getTargetTemperature()
                logger.info("Evaluating target temperature now: $targetTemperature")
                val energyPriceOK: Boolean = tibberService.isEnergyPriceOK(24 - 7)
                if (energyPriceOK && temperatureAndHeaterStatus.temp < targetTemperature) {
                    logger.info("Setting heater to on")
                    persistenceService[HEATER_STATUS_KEY] = "on"
                    lastSwitchedTimestamp = System.currentTimeMillis()
                } else {
                    logger.info(
                        "Setting heater to off. energyPriceOK={}",
                        energyPriceOK
                    )
                    persistenceService[HEATER_STATUS_KEY] = "off"
                    lastSwitchedTimestamp = System.currentTimeMillis()
                }
            }
        }

        // bring the heater to the desired state
        if (temperatureAndHeaterStatus.heaterIsOn && "off" == getConfiguredHeaterTarget()) {
            return executeCommand(commandSwitchOffHeater) != null
        } else if (!temperatureAndHeaterStatus.heaterIsOn && "on" == getConfiguredHeaterTarget()) {
            return executeCommand(commandSwitchOnHeater) != null
        }
        return true
    }

    fun onRfMessage(rfPacket: RfPacket) {
        if (rfPacket.remoteAddr != rfAddr) {
            return
        }

        msgListener?.let {
            it(rfPacket)
        }
    }

    private fun recordRfValues(
        p: RfPacket
    ): TemperatureAndHeaterStatus {
        val temperature: Int = Sht15SensorService.getTemperature(p)
        val temp: String = divideBy100(temperature)
        val humidity: String = divideBy100(getRelativeHumidity(p, temperature))
        val heaterStatus = p.message[4].toInt() == 1

        logger.info("Relative humidity $humidity")
        logger.info("Temperature $temp")
        logger.info("Heater on? $heaterStatus")

        influxDBClient.recordSensorData(
            "sensor",
            listOf(
                "temperature" to temp,
                "humidity" to humidity,
                "heater_status" to (if (heaterStatus) 1 else 0).toString(),
            ),
            "room" to "heating_controller"
        )

        return TemperatureAndHeaterStatus(temperature, heaterStatus)
    }

    private fun recordLocalValues(
        currentMode: Mode,
        failedAttempts: Int
    ) {
        influxDBClient.recordSensorData(
            "sensor",
            listOf(
                "manual_mode" to (if (currentMode == Mode.MANUAL) 1 else 0).toString(),
                "target_temperature" to divideBy100(getTargetTemperature()),
                "configured_heater_target" to (if (getConfiguredHeaterTarget() == "on") 1 else 0).toString(),
                "failed_attempts" to failedAttempts.toString()
            ),
            "room" to "heating_controller"
        )
    }

    private fun executeCommand(command: Int): RfPacket? {
        val finalCommand = if (failedAttempts.get() > 10) {
            logger.warn("Overriding command $command with OFF because of too many failed attempts ${failedAttempts.get()}")
            commandSwitchOffHeater
        } else command
        val inMsg = LinkedBlockingQueue<RfPacket>()
        msgListener = { rfPacket -> inMsg.offer(rfPacket) }
        try {
            repeat(5) {
                serialConnection.send(RfPacket(rfAddr, ByteArray(1) { finalCommand.toByte() }))
                val response = inMsg.poll(500, TimeUnit.MILLISECONDS)
                if (response != null) {
                    return response
                }
                Thread.sleep(1000)
            }
        } finally {
            msgListener = null
        }
        return null
    }

    private fun getCurrentMode() = Mode.valueOf(
        persistenceService[OPERATING_MODE, Mode.OFF.name]!!
    )

    private fun getTargetTemperature() =
        Integer.valueOf(persistenceService[TARGET_TEMP_KEY, (25 * 100).toString()])

    private fun getConfiguredHeaterTarget() = persistenceService[HEATER_STATUS_KEY, "off"]!!


}

enum class Mode {
    ON, OFF, MANUAL
}

data class TemperatureAndHeaterStatus(
    val temp: Int,
    val heaterIsOn: Boolean
)
