package com.dehnes.smarthome.zwave

import com.dehnes.smarthome.api.dtos.StairsHeatingRequest
import com.dehnes.smarthome.api.dtos.StairsHeatingResponse
import com.dehnes.smarthome.api.dtos.StairsHeatingType.*
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.toInt
import com.dehnes.smarthome.utils.withLogging
import com.dehnes.smarthome.victron.doubleValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

data class StairsHeatingSettings(
    val outsideTemperatureRangeFrom: Double = -2.0,
    val outsideTemperatureRangeTo: Double = 1.0,
    val targetTemperature: Double = 5.0,
    val enabled: Boolean = true,
)

data class StairsHeatingData(
    val currentState: Boolean,
    val temperature: Double,
    val current: Double,
    val createdAt: Instant,
)

data class OutsideTemperature(
    val temperature: Double,
    val createdAt: Instant,
)

typealias ZWaveRpcCallback = (Any) -> Unit

class StairsHeatingService(
    private val zWaveMqttClient: ZWaveMqttClient,
    private val clock: Clock,
    private val influxDBClient: InfluxDBClient,
    private val quickStatsService: QuickStatsService,
    private val configService: ConfigService,
    private val executorService: ExecutorService,
    private val userSettingsService: UserSettingsService,
    private val nodeId: Int = 6,
    private val mqttName: String = "zwave-js-ui",
    private val refreshDelaySeconds: Long = 60,
) {

    private val logger = KotlinLogging.logger { }
    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lock = ReentrantLock()

    private var outsideTemperature: OutsideTemperature? = null
    private val ongoingRequests = mutableMapOf<String, ZWaveRpcCallback>()
    private var lastData: StairsHeatingData? = null

    fun init() {
        quickStatsService.listeners[UUID.randomUUID().toString()] = {
            outsideTemperature = OutsideTemperature(it.outsideTemperature, clock.instant())
        }
        zWaveMqttClient.addListener(
            Listener(
                UUID.randomUUID().toString(),
                "$nodeId/#",
                this::onMsg
            )
        )

        scheduledExecutorService.scheduleAtFixedRate({
            executorService.submit(withLogging {
                evaluate()
            })

        }, 0, refreshDelaySeconds, TimeUnit.SECONDS)
    }

    private fun <T> doLocked(fn: () -> T): T {
        val r: T
        lock.lock()
        try {
            r = fn()
        } finally {
            lock.unlock()
        }
        return r
    }

    private fun getSwitchState(): Boolean {
        val cnt = CountDownLatch(1)
        val result = AtomicReference(false)
        doLocked {
            ongoingRequests["/$nodeId/37/1/currentValue"] = {
                result.set(it as Boolean)
                cnt.countDown()
            }
        }
        zWaveMqttClient.sendAny(
            "_CLIENTS/ZWAVE_GATEWAY-$mqttName/api/refreshCCValues/set",
            mapOf(
                "args" to listOf(nodeId, 37) // switch is parameter 37
            )
        )

        check(cnt.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun getTemperature(): Double {
        val cnt = CountDownLatch(1)
        val result = AtomicReference(0.0)
        doLocked {
            ongoingRequests["/$nodeId/49/2/Air_temperature"] = {
                result.set(doubleValue(it))
                cnt.countDown()
            }
        }
        zWaveMqttClient.sendAny(
            "_CLIENTS/ZWAVE_GATEWAY-$mqttName/api/refreshCCValues/set",
            mapOf(
                "args" to listOf(nodeId, 49) // temp is parameter 49
            )
        )

        check(cnt.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun getCurrent(): Double {
        val cnt = CountDownLatch(1)
        val result = AtomicReference(0.0)
        doLocked {
            ongoingRequests["/$nodeId/50/1/value/66817"] = {
                result.set(doubleValue(it))
                cnt.countDown()
            }
        }
        zWaveMqttClient.sendAny(
            "_CLIENTS/ZWAVE_GATEWAY-$mqttName/api/refreshCCValues/set",
            mapOf(
                "args" to listOf(nodeId, 50) // meter is parameter 50
            )
        )

        check(cnt.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    fun evaluate() {
        logger.debug { "Eval()" }
        val currentState = getSwitchState()
        val temperature = getTemperature()
        val current = getCurrent()


        if (lastData != null) {
            val range = (lastData!!.temperature - 3.0)..(lastData!!.temperature + 3.0)
            check(temperature in range) { "Temperature $temperature changed too much. range=$range" }
        }

        lastData = StairsHeatingData(
            currentState,
            temperature,
            current,
            clock.instant()
        )

        logger.debug { "newData=$lastData" }

        influxDBClient.recordSensorData(
            listOf(
                InfluxDBRecord(
                    clock.instant(), "sensor", listOfNotNull(
                        "ampere" to current.toString(),
                        "temperature" to temperature.toString(),
                        "on_off" to currentState.toInt().toString(),
                    ).toMap(), mapOf("room" to "varmekabel_trapp")
                )
            )
        )

        val outsideTemperature = this.outsideTemperature

        if (outsideTemperature == null) {
            logger.warn { "No outsideTemperature available" }
            return
        }

        if (outsideTemperature.createdAt.isBefore(clock.instant().minusSeconds(refreshDelaySeconds * 2))) {
            logger.warn { "outsideTemperature too old, is quicksettings not working?" }
            return
        }

        val settings = configService.getStairsHeatingSettings()

        val outsideRange = settings.outsideTemperatureRangeFrom..settings.outsideTemperatureRangeTo

        val targetState = when {
            !settings.enabled -> {
                if (currentState) {
                    logger.debug { "Switching off due to disabled" }
                }
                false
            }

            outsideTemperature.temperature !in outsideRange -> {
                if (currentState) {
                    logger.debug { "Disable because outside temp $outsideTemperature is outside of range $outsideRange" }
                }
                false
            }

            temperature < settings.targetTemperature -> {
                if (!currentState) {
                    logger.debug { "Switching on" }
                }
                true
            }

            temperature >= settings.targetTemperature -> {
                if (currentState) {
                    logger.debug { "Switching off" }
                }
                false
            }

            else -> error("Impossible")
        }

        if (currentState != targetState) {
            logger.debug { "Sending $targetState" }
            zWaveMqttClient.sendAny(
                "$nodeId/37/1/targetValue/set", mapOf(
                    "value" to targetState
                )
            )
        }
    }

    fun onMsg(topic: String, data: Map<String, Any>) {
        val callback = doLocked {
            ongoingRequests.remove(topic)
        }

        if (callback != null) {
            callback(data["value"]!!)
        } else {
            logger.debug { "Ignoring message from $topic" }
        }
    }

    fun handleRequest(userId: String?, stairsHeatingRequest: StairsHeatingRequest): StairsHeatingResponse {
        when (stairsHeatingRequest.type) {
            get -> {}
            enableDisable -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(enabled = !it.enabled)
                }
            }

            increaseTargetTemp -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(targetTemperature = it.targetTemperature + 1.0)
                }
            }

            decreaseTargetTemp -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(targetTemperature = it.targetTemperature - 1.0)
                }
            }

            increaseOutsideLowerTemp -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(outsideTemperatureRangeFrom = it.outsideTemperatureRangeFrom + 1.0)
                }
            }

            decreaseOutsideLowerTemp -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(outsideTemperatureRangeFrom = it.outsideTemperatureRangeFrom - 1.0)
                }
            }

            increaseOutsideUpperTemp -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(outsideTemperatureRangeTo = it.outsideTemperatureRangeTo + 1.0)
                }
            }

            decreaseOutsideUpperTemp -> {
                check(userSettingsService.canUserWrite(userId, UserRole.heaterStairs))
                configService.updateStairsHeatingSettings {
                    it.copy(outsideTemperatureRangeTo = it.outsideTemperatureRangeTo - 1.0)
                }
            }
        }

        check(userSettingsService.canUserRead(userId, UserRole.heaterStairs))
        return StairsHeatingResponse(
            lastData,
            configService.getStairsHeatingSettings()
        )
    }

}
