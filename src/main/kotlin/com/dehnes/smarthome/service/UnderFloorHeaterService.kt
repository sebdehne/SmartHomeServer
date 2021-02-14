package com.dehnes.smarthome.service

import com.dehnes.smarthome.api.*
import com.dehnes.smarthome.external.InfluxDBClient
import com.dehnes.smarthome.external.RfPacket
import com.dehnes.smarthome.external.SerialConnection
import com.dehnes.smarthome.math.Sht15SensorService
import com.dehnes.smarthome.math.Sht15SensorService.getRelativeHumidity
import com.dehnes.smarthome.math.divideBy100
import mu.KotlinLogging
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

private const val TARGET_TEMP_KEY = "HeatingControllerService.targetTemp"
private const val HEATER_STATUS_KEY = "HeatingControllerService.heaterTarget"
private const val OPERATING_MODE = "HeatingControllerService.operatingMode"
private const val MOSTEXPENSIVEHOURSTOSKIP_KEY = "HeatingControllerService.mostExpensiveHoursToSkip"

class UnderFloorHeaterService(
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

    private var lastTick: Long = 0
    private var msgListener: ((RfPacket) -> Unit)? = null

    val listeners = ConcurrentHashMap<String, (UnderFloorHeaterStatus) -> Unit>()

    @Volatile
    private var lastStatus: UnderFloorHeaterStatus? = null

    fun start() {
        timer.scheduleAtFixedRate({
            executorService.submit {
                try {
                    tick()
                } catch (e: Exception) {
                    logger.error("", e)
                }
            }
        }, 30, 30, TimeUnit.SECONDS) // TODO use 2 min
    }

    private fun tick() = if (runLock.tryLock()) {
        try {
            tickLocked()
        } finally {
            runLock.unlock()
        }
    } else {
        false
    }

    private fun tickLocked(): Boolean {
        val currentMode: Mode = getCurrentMode()
        logger.info("Current mode: $currentMode")

        if (lastTick > System.currentTimeMillis() - 10000) {
            return true
        }
        lastTick = System.currentTimeMillis()

        // request measurement
        val rfPacket: RfPacket? = executeCommand(commandReadStatus)
        // TODO ignore values too unrealistic / remember last values in memory -
        recordLocalValues(currentMode, failedAttempts.get())
        val temperatureAndHeaterStatus = rfPacket?.let { parsePacket(it, true) } ?: return false
        var energyPriceCurrentlyTooExpensive = false

        // evaluate state
        when (currentMode) {
            Mode.OFF -> persistenceService[HEATER_STATUS_KEY] = "off"
            Mode.ON -> persistenceService[HEATER_STATUS_KEY] = "on"
            Mode.MANUAL -> {
                val targetTemperature = getTargetTemperature()
                logger.info("Evaluating target temperature now: $targetTemperature")
                energyPriceCurrentlyTooExpensive = !tibberService.isEnergyPriceOK(24 - getMostExpensiveHoursToSkip())
                if (!energyPriceCurrentlyTooExpensive && temperatureAndHeaterStatus.temp < targetTemperature) {
                    logger.info("Setting heater to on")
                    persistenceService[HEATER_STATUS_KEY] = "on"
                } else {
                    logger.info {
                        "Setting heater to off. energyPriceCurrentlyTooExpensive=$energyPriceCurrentlyTooExpensive"
                    }
                    persistenceService[HEATER_STATUS_KEY] = "off"
                }
            }
        }

        var heaterStatus = temperatureAndHeaterStatus.heaterIsOn

        // bring the heater to the desired state
        val executionResult = if (temperatureAndHeaterStatus.heaterIsOn && "off" == getConfiguredHeaterTarget()) {
            executeCommand(commandSwitchOffHeater)?.let {
                heaterStatus = parsePacket(it, false).heaterIsOn
                true
            } ?: false
        } else if (!temperatureAndHeaterStatus.heaterIsOn && "on" == getConfiguredHeaterTarget()) {
            executeCommand(commandSwitchOnHeater)?.let {
                heaterStatus = parsePacket(it, false).heaterIsOn
                true
            } ?: false
        } else {
            true
        }

        lastStatus = UnderFloorHeaterStatus(
            UnderFloorHeaterMode.values().first { it.mode == currentMode },
            if (heaterStatus) OnOff.on else OnOff.off,
            temperatureAndHeaterStatus.temp,
            UnderFloorHeaterConstantTemperaturStatus(getTargetTemperature(), getMostExpensiveHoursToSkip(), energyPriceCurrentlyTooExpensive)
        )

        listeners.forEach {
            try {
                it.value(lastStatus!!)
            } catch (e: Exception) {
                logger.error("", e)
            }
        }

        return executionResult
    }

    fun onRfMessage(rfPacket: RfPacket) {
        if (rfPacket.remoteAddr != rfAddr) {
            return
        }

        msgListener?.let {
            it(rfPacket)
        } ?: run {
            logger.info { "Could not use packet because no listener: $rfPacket" }
        }
    }

    fun getCurrentState() = lastStatus

    fun update(updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode): Boolean {
        if (updateUnderFloorHeaterMode.newTargetTemperature != null) {
            check(updateUnderFloorHeaterMode.newTargetTemperature in 1000..5000)
            setTargetTemperature(updateUnderFloorHeaterMode.newTargetTemperature)
        }
        if (updateUnderFloorHeaterMode.newMostExpensiveHoursToSkip != null) {
            check(updateUnderFloorHeaterMode.newTargetTemperature in 0..24)
            setMostExpensiveHoursToSkip(updateUnderFloorHeaterMode.newMostExpensiveHoursToSkip)
        }
        setCurrentMode(updateUnderFloorHeaterMode.newMode.mode)
        return tick()
    }

    private fun parsePacket(
        p: RfPacket,
        record: Boolean
    ): TemperatureAndHeaterStatus {
        val temperature: Int = Sht15SensorService.getTemperature(p)
        val temp: String = divideBy100(temperature)
        val humidity: String = divideBy100(getRelativeHumidity(p, temperature))
        val heaterStatus = p.message[4].toInt() == 1

        logger.info("Relative humidity $humidity")
        logger.info("Temperature $temp")
        logger.info("Heater on? $heaterStatus")

        if (record) {
            influxDBClient.recordSensorData(
                "sensor",
                listOf(
                    "temperature" to temp,
                    "humidity" to humidity,
                    "heater_status" to (if (heaterStatus) 1 else 0).toString(),
                ),
                "room" to "heating_controller"
            )
        }

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

    private fun setCurrentMode(m: Mode) {
        persistenceService[OPERATING_MODE] = m.name
    }

    private fun getTargetTemperature() =
        Integer.valueOf(persistenceService[TARGET_TEMP_KEY, (25 * 100).toString()])

    private fun setTargetTemperature(t: Int) {
        persistenceService[TARGET_TEMP_KEY] = t.toString()
    }

    private fun getMostExpensiveHoursToSkip() =
        Integer.valueOf(persistenceService[MOSTEXPENSIVEHOURSTOSKIP_KEY, 7.toString()])

    private fun setMostExpensiveHoursToSkip(t: Int) {
        persistenceService[MOSTEXPENSIVEHOURSTOSKIP_KEY] = t.toString()
    }


    private fun getConfiguredHeaterTarget() = persistenceService[HEATER_STATUS_KEY, "off"]!!

}

enum class Mode {
    ON, OFF, MANUAL
}

data class TemperatureAndHeaterStatus(
    val temp: Int,
    val heaterIsOn: Boolean
)
