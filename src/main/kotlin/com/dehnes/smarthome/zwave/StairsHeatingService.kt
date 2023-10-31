package com.dehnes.smarthome.zwave

import com.dehnes.smarthome.api.dtos.StairsHeatingRequest
import com.dehnes.smarthome.api.dtos.StairsHeatingResponse
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.utils.toInt
import com.dehnes.smarthome.utils.withLogging
import com.dehnes.smarthome.victron.doubleValue
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

data class StairsHeatingSettings(
    val outsideTemperatureRangeFrom: Double = -2.0,
    val outsideTemperatureRangeTo: Double = 1.0,
    val targetTemperature: Double = 5.0,
    val enabled: Boolean = true,
)

data class StairsHeatingDataIncomplete(
    val temperature: Double? = null,
    val watt: Double? = null,
    val kWhConsumed: Double? = null,
    val ampere: Double? = null,
    val switchState: Boolean? = null,
) {
    fun hasAllData() = temperature != null
            && watt != null
            && kWhConsumed != null
            && ampere != null
            && switchState != null

    fun complete(settings: StairsHeatingSettings) = StairsHeatingData(
        temperature!!,
        watt!!,
        kWhConsumed!!,
        ampere!!,
        switchState!!,
        settings,
    )
}

data class StairsHeatingData(
    val temperature: Double,
    val watt: Double,
    val kWhConsumed: Double,
    val ampere: Double,
    val switchState: Boolean,
    val settings: StairsHeatingSettings,
    val createdAt: Instant = Instant.now()
)

data class OutsideTemperature(
    val temperature: Double,
    val createdAt: Instant = Instant.now()
)

class StairsHeatingService(
    private val zWaveMqttClient: ZWaveMqttClient,
    private val clock: Clock,
    private val influxDBClient: InfluxDBClient,
    private val quickStatsService: QuickStatsService,
    private val configService: ConfigService,
    private val executorService: ExecutorService,
    private val nodeId: Int = 4,
    private val mqttName: String = "zwave-js-ui",
    private val refreshDelaySeconds: Long = 10,
) {

    private val logger = KotlinLogging.logger { }
    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lock = ReentrantLock()

    @Volatile
    private var currentState: StairsHeatingData? = null
    private var currentlyReceiving: StairsHeatingDataIncomplete = StairsHeatingDataIncomplete()
    private var outsideTemperature: OutsideTemperature? = null

    fun init() {
        quickStatsService.listeners[UUID.randomUUID().toString()] = {
            outsideTemperature = OutsideTemperature(it.outsideTemperature)
        }
        zWaveMqttClient.addListener(
            Listener(
                UUID.randomUUID().toString(),
                "$nodeId/#", // nodeId 4
                this::onMsg
            )
        )

        scheduledExecutorService.scheduleAtFixedRate({
            executorService.submit(withLogging {
                logger.info { "Calling eval()" }
                evaluate()

                // ask for a refresh
                currentlyReceiving = StairsHeatingDataIncomplete()

                logger.info { "Sending refresh request" }
                zWaveMqttClient.sendAny(
                    "_CLIENTS/ZWAVE_GATEWAY-$mqttName/api/refreshValues/set",
                    mapOf(
                        "args" to listOf(nodeId)
                    )
                )
            })

        }, 0, refreshDelaySeconds, TimeUnit.SECONDS)
    }

    fun evaluate() {
        val currentState = this.currentState
        val outsideTemperature = this.outsideTemperature

        if (currentState == null) {
            logger.info { "No data available" }
            return
        }
        if (outsideTemperature == null) {
            logger.info { "No outsideTemperature available" }
            return
        }

        if (currentState.createdAt.isBefore(Instant.now().minusSeconds(refreshDelaySeconds * 2))) {
            logger.warn { "Data too old, is refresh not working?" }
            return
        }
        if (outsideTemperature.createdAt.isBefore(Instant.now().minusSeconds(refreshDelaySeconds * 2))) {
            logger.warn { "outsideTemperature too old, is quicksettings not working?" }
            return
        }

        val settings = configService.getStairsHeatingSettings()

        val outsideRange = settings.outsideTemperatureRangeFrom..settings.outsideTemperatureRangeTo

        val targetState = when {
            !settings.enabled -> {
                if (currentState.switchState) {
                    logger.info { "Switching off due to disabled" }
                }
                false
            }

            outsideTemperature.temperature !in outsideRange -> {
                logger.debug { "Disable because outside temp $outsideTemperature is outside of range $outsideRange" }
                false
            }

            currentState.temperature < currentState.settings.targetTemperature -> {
                if (!currentState.switchState) {
                    logger.info { "Switching on" }
                }
                true
            }

            currentState.temperature > currentState.settings.targetTemperature -> {
                if (currentState.switchState) {
                    logger.info { "Switching off" }
                }
                false
            }

            else -> error("Impossible")
        }

        if (currentState.switchState != targetState) {
            logger.info { "Sending $targetState" }
            zWaveMqttClient.sendAny(
                "4/37/1/targetValue/set", mapOf(
                    "value" to targetState
                )
            )
        }

    }

    fun onMsg(topic: String, data: Map<String, Any>) {

        var dbRecord: InfluxDBRecord? = null

        lock.lock()
        try {

            when (topic) {
                "/$nodeId/50/1/value/65537" -> {
                    // Electric_kWh_Consumed
                    val kwhConsumed = doubleValue(data["value"])
                    currentlyReceiving = currentlyReceiving.copy(
                        kWhConsumed = kwhConsumed
                    )
                }

                "/$nodeId/50/1/value/66049" -> {
                    // Electric_W_Consumed
                    val watt = doubleValue(data["value"])
                    currentlyReceiving = currentlyReceiving.copy(
                        watt = watt
                    )
                }

                "/$nodeId/50/1/value/66817" -> {
                    // Electric_A_Consumed
                    val ampere = doubleValue(data["value"])
                    currentlyReceiving = currentlyReceiving.copy(
                        ampere = ampere
                    )
                }

                "/$nodeId/49/2/Air_temperature" -> {
                    // Air temperature
                    val temperature = doubleValue(data["value"])

                    // dismiss incorrect values
                    if (currentlyReceiving.temperature != null) {
                        val range = (currentlyReceiving.temperature!! - 3.0)..(currentlyReceiving.temperature!! + 3.0)
                        if (temperature !in range) {
                            logger.warn { "Ignoring extreme value=$temperature (range=$range)" }
                            return
                        }
                    }

                    currentlyReceiving = currentlyReceiving.copy(
                        temperature = temperature
                    )
                }

                "/$nodeId/37/1/currentValue" -> {
                    val switchState = data["value"] as Boolean
                    currentlyReceiving = currentlyReceiving.copy(
                        switchState = switchState
                    )
                }

                else -> logger.info { "Ignoring message from $topic" }
            }


            if (currentlyReceiving.hasAllData()) {
                logger.info { "hasAllData" }
                val complete = currentlyReceiving.complete(
                    configService.getStairsHeatingSettings()
                )

                currentState = complete
                currentlyReceiving = StairsHeatingDataIncomplete()

                dbRecord = InfluxDBRecord(
                    clock.instant(), "sensor", listOfNotNull(
                        "kWhConsumed" to complete.kWhConsumed.toString(),
                        "watt" to complete.watt.toString(),
                        "ampere" to complete.ampere.toString(),
                        "temperature" to complete.temperature.toString(),
                        "on_off" to complete.switchState.toInt().toString(),
                    ).toMap(), mapOf("room" to "varmekabel_trapp")
                )
            }

        } finally {
            lock.unlock()
        }

        if (dbRecord != null) {
            influxDBClient.recordSensorData(listOf(dbRecord))
        }

    }

    fun handleRequest(stairsHeatingRequest: StairsHeatingRequest): StairsHeatingResponse {
        TODO()
    }

}

