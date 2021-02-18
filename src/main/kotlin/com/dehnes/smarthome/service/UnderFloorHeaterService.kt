package com.dehnes.smarthome.service

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.external.InfluxDBClient
import com.dehnes.smarthome.external.RfPacket
import com.dehnes.smarthome.external.SerialConnection
import com.dehnes.smarthome.math.Sht15SensorService
import com.dehnes.smarthome.math.Sht15SensorService.getRelativeHumidity
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue

private const val TARGET_TEMP_KEY = "HeatingControllerService.targetTemp"
private const val HEATER_STATUS_KEY = "HeatingControllerService.heaterTarget"
private const val OPERATING_MODE = "HeatingControllerService.operatingMode"
private const val MOSTEXPENSIVEHOURSTOSKIP_KEY = "HeatingControllerService.mostExpensiveHoursToSkip"

class UnderFloorHeaterService(
    private val serialConnection: SerialConnection,
    executorService: ExecutorService,
    private val persistenceService: PersistenceService,
    private val influxDBClient: InfluxDBClient,
    private val tibberService: TibberService
) : AbstractProcess(executorService, 120) {

    private val rfAddr = 27
    private val commandReadStatus = 1
    private val commandSwitchOnHeater = 2
    private val commandSwitchOffHeater = 3

    private val logger = KotlinLogging.logger { }
    private val failedAttempts = AtomicInteger(0)

    private var lastTick: Long = 0
    private var msgListener: ((RfPacket) -> Unit)? = null

    val listeners = ConcurrentHashMap<String, (UnderFloorHeaterStatus) -> Unit>()

    @Volatile
    private var lastStatus: UnderFloorHeaterStatus? = null
    private var previousValue: UnderFloorSensorData? = null

    override fun logger() = logger

    override fun tickLocked(): Boolean {
        val currentMode: Mode = getCurrentMode()
        logger.info("Current mode: $currentMode")

        if (lastTick > System.currentTimeMillis() - 10000) {
            return true
        }
        lastTick = System.currentTimeMillis()

        // request measurement
        val rfPacket: RfPacket? = executeCommand(commandReadStatus)
        recordLocalValues(currentMode, failedAttempts.get())
        val sensorData = rfPacket?.let { parsePacket(it, true) } ?: return false
        if (!accept(sensorData)) {
            return false
        }

        var energyPriceCurrentlyTooExpensive = false

        // evaluate state
        when (currentMode) {
            Mode.OFF -> persistenceService[HEATER_STATUS_KEY] = "off"
            Mode.ON -> persistenceService[HEATER_STATUS_KEY] = "on"
            Mode.MANUAL -> {
                val targetTemperature = getTargetTemperature()
                logger.info("Evaluating target temperature now: $targetTemperature")
                energyPriceCurrentlyTooExpensive = !tibberService.isEnergyPriceOK(24 - getMostExpensiveHoursToSkip())
                if (!energyPriceCurrentlyTooExpensive && sensorData.temperature < targetTemperature) {
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

        var heaterStatus = sensorData.heaterIsOn

        // bring the heater to the desired state
        val executionResult = if (sensorData.heaterIsOn && "off" == getConfiguredHeaterTarget()) {
            executeCommand(commandSwitchOffHeater)?.let {
                heaterStatus = parsePacket(it, false).heaterIsOn
                true
            } ?: false
        } else if (!sensorData.heaterIsOn && "on" == getConfiguredHeaterTarget()) {
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
            sensorData.temperature,
            UnderFloorHeaterConstantTemperaturStatus(
                getTargetTemperature(),
                getMostExpensiveHoursToSkip(),
                energyPriceCurrentlyTooExpensive
            )
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

    private fun accept(sensorData: UnderFloorSensorData): Boolean {
        val previous = previousValue
        return if (previous == null || previous.ageInSeconds() > 15 * 60) {
            previousValue = sensorData
            true
        } else {
            val delta = ((sensorData.temperature - previous.temperature).absoluteValue) / 100
            (delta <= 5).apply {
                if (!this) {
                    logger.info("Ignoring abnormal values. previous=$previous")
                }
            }
        }
    }

    private fun parsePacket(
        p: RfPacket,
        record: Boolean
    ): UnderFloorSensorData {
        val sensorData = UnderFloorSensorData.fromRfPacket(p)

        logger.info { "Received sensorData=$sensorData" }

        if (record) {
            influxDBClient.recordSensorData(
                "sensor",
                listOf(
                    "temperature" to sensorData.toTemperature(),
                    "humidity" to sensorData.toHumidity(),
                    "heater_status" to (if (sensorData.heaterIsOn) 1 else 0).toString(),
                ),
                "room" to "heating_controller"
            )
        }

        return sensorData
    }

    private fun recordLocalValues(
        currentMode: Mode,
        failedAttempts: Int
    ) {
        influxDBClient.recordSensorData(
            "sensor",
            listOf(
                "manual_mode" to (if (currentMode == Mode.MANUAL) 1 else 0).toString(),
                "target_temperature" to (getTargetTemperature().toFloat() / 100).toString(),
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
                serialConnection.send(RfPacket(rfAddr, intArrayOf(finalCommand)))
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

data class TemperatureAndHeaterStatus(
    val temp: Int,
    val heaterIsOn: Boolean
)

data class UnderFloorSensorData(
    val temperature: Int,
    val humidity: Int,
    val heaterIsOn: Boolean,
    val receivedAt: Instant = Instant.now()
) {
    companion object {
        fun fromRfPacket(p: RfPacket): UnderFloorSensorData {
            val temperature = Sht15SensorService.getTemperature(p)
            val humidity = getRelativeHumidity(p, temperature)
            val heaterStatus = p.message[4] == 1

            return UnderFloorSensorData(
                temperature,
                humidity,
                heaterStatus
            )
        }
    }

    fun toTemperature() = (temperature.toFloat() / 100).toString()
    fun toHumidity() = (humidity.toFloat() / 100).toString()

    override fun toString(): String {
        return "UnderFloorSensorData(temperature=${toTemperature()}, humidity=${toHumidity()}, heaterIsOn=$heaterIsOn, receivedAt=$receivedAt)"
    }

    fun ageInSeconds() = (System.currentTimeMillis() - receivedAt.toEpochMilli()) / 1000

}