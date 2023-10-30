package com.dehnes.smarthome.zwave

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.utils.toInt
import com.dehnes.smarthome.victron.doubleValue
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

data class VarmeKabelTrappSettings(
    val outsideTemperatureRangeFrom: Double = -2.0,
    val outsideTemperatureRangeTo: Double = 1.0,
    val targetTemperatureFrom: Double = 3.0,
    val targetTemperatureTo: Double = 5.0,
)

data class VarmeKabelTrappData(
    val temperature: Double? = null,
    val watt: Double? = null,
    val kWhConsumed: Double? = null,
    val ampere: Double? = null,
    val switchState: Boolean? = null,
)

class VarmeKabelTrappService(
    private val zWaveMqttClient: ZWaveMqttClient,
    private val clock: Clock,
    private val influxDBClient: InfluxDBClient,
    private val quickStatsService: QuickStatsService,
    private val configService: ConfigService,
    private val nodeId: Int = 4,
    private val mqttName: String = "zwave-js-ui",
    private val refreshDelaySeconds: Long = 10,
) {

    private val logger = KotlinLogging.logger { }
    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lock = ReentrantLock()

    private var currentState = VarmeKabelTrappData()
    private var outsideTemperature: Double? = null
    private var outsideTemperatureAt: Instant? = null

    fun init() {
        quickStatsService.listeners[UUID.randomUUID().toString()] = {
            outsideTemperature = it.outsideTemperature
            outsideTemperatureAt = Instant.now()
        }
        zWaveMqttClient.addListener(
            Listener(
                UUID.randomUUID().toString(), "$nodeId/#", // nodeId 4
                this::onMsg
            )
        )

        scheduledExecutorService.scheduleAtFixedRate({

            evaluate()

            zWaveMqttClient.sendAny(
                "_CLIENTS/ZWAVE_GATEWAY-$mqttName/api/refreshValues/set", mapOf("args" to listOf(nodeId))
            )

            val dbRecord: InfluxDBRecord

            if (lock.tryLock()) {
                try {
                    dbRecord = InfluxDBRecord(
                        clock.instant(), "sensor", listOfNotNull(
                            currentState.kWhConsumed?.let { "kWhConsumed" to it.toString() },
                            currentState.watt?.let { "watt" to it.toString() },
                            currentState.ampere?.let { "ampere" to it.toString() },
                            currentState.temperature?.let { "temperature" to it.toString() },
                            currentState.switchState?.let { "on_off" to it.toInt().toString() },
                        ).toMap(), mapOf("room" to "varmekabel_trapp")
                    )

                } finally {
                    lock.unlock()
                }

                influxDBClient.recordSensorData(listOf(dbRecord))
            }
        }, refreshDelaySeconds, refreshDelaySeconds, TimeUnit.SECONDS)
    }

    fun evaluate() {
        val temp: Double
        val state: Boolean

        logger.info { "Current data $currentState" }

        lock.lock()
        try {
            temp = currentState.temperature ?: return
            state = currentState.switchState ?: return
        } finally {
            lock.unlock()
        }

        if (outsideTemperature == null || outsideTemperatureAt == null || outsideTemperatureAt!!.isBefore(
                Instant.now().minusSeconds(900)
            )
        ) {
            logger.warn { "Cannot operate, no outside temperature update received" }
            return
        }

        val settings = configService.getVarmeKabelTrappSettings()

        val outsideRange = settings.outsideTemperatureRangeFrom..settings.outsideTemperatureRangeTo
        val stairTargetTemp = settings.targetTemperatureFrom..settings.targetTemperatureTo
        val targetState = if (outsideTemperature!! !in outsideRange) {
            logger.debug { "Disable because outside temp $outsideTemperature is outside of range $outsideRange" }
            false
        } else if (state && temp in stairTargetTemp) {
            logger.debug { "Keeping on" }
            true
        } else if (!state && temp in stairTargetTemp) {
            logger.info { "Switching on" }
            true
        } else if (state && temp !in stairTargetTemp) {
            logger.info { "Switching off" }
            false
        } else if (!state && temp !in stairTargetTemp) {
            logger.debug { "Keeping off" }
            false
        } else error("Impossible")

        if (state != targetState) {
            logger.info { "Sending $targetState" }
            zWaveMqttClient.sendAny(
                "/4/37/1/targetValue/set", mapOf(
                    "value" to targetState
                )
            )
        }

    }

    fun onMsg(topic: String, data: Map<String, Any>) {

        lock.lock()
        try {

            when (topic) {
                "/$nodeId/50/1/value/65537" -> {
                    // Electric_kWh_Consumed
                    val kwhConsumed = doubleValue(data["value"])
                    currentState = currentState.copy(
                        kWhConsumed = kwhConsumed
                    )
                }

                "/$nodeId/50/1/value/66049" -> {
                    // Electric_W_Consumed
                    val watt = doubleValue(data["value"])
                    currentState = currentState.copy(
                        watt = watt
                    )
                }

                "/$nodeId/50/1/value/66817" -> {
                    // Electric_A_Consumed
                    val ampere = doubleValue(data["value"])
                    currentState = currentState.copy(
                        ampere = ampere
                    )
                }

                "/$nodeId/49/2/Air_temperature" -> {
                    // Air temperature
                    val temperature = doubleValue(data["value"])
                    currentState = currentState.copy(
                        temperature = temperature
                    )
                }

                "/$nodeId/37/1/currentValue" -> {
                    val switchState = data["value"] as Boolean
                    currentState = currentState.copy(
                        switchState = switchState
                    )
                }

                else -> logger.debug { "Ignoring message from $topic" }
            }
        } finally {
            lock.unlock()
        }

    }

}

