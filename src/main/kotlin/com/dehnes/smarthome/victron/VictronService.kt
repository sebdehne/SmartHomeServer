package com.dehnes.smarthome.victron

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hivemq.client.internal.mqtt.datatypes.MqttTopicFilterImpl
import com.hivemq.client.internal.mqtt.datatypes.MqttTopicImpl
import com.hivemq.client.internal.mqtt.datatypes.MqttUserPropertiesImpl
import com.hivemq.client.internal.mqtt.message.publish.MqttWillPublish
import com.hivemq.client.internal.mqtt.message.subscribe.MqttSubscribe
import com.hivemq.client.internal.mqtt.message.subscribe.MqttSubscription
import com.hivemq.client.internal.util.collections.ImmutableList
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class VictronService(
    victronHost: String,
    private val objectMapper: ObjectMapper,
    private val executorService: ExecutorService,
    private val persistenceService: PersistenceService,
    private val influxDBClient: InfluxDBClient,
) {

    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    val asyncClient = MqttClient.builder()
        .identifier(UUID.randomUUID().toString())
        .serverAddress(InetSocketAddress(victronHost, 1883))
        .useMqttVersion5()
        .automaticReconnectWithDefaultConfig()
        .buildAsync()

    val listeners = ConcurrentHashMap<String, (data: ESSValues) -> Unit>()

    @Volatile
    var essValues = ESSValues()
    var lastNotify = System.currentTimeMillis()
    val delayInMs = 2 * 1000L


    companion object {
        val logger = KotlinLogging.logger { }
    }

    init {
        asyncClient.connect().get(20, TimeUnit.SECONDS)
        asyncClient.subscribe(
            MqttSubscribe(
                ImmutableList.of(
                    MqttSubscription(
                        MqttTopicFilterImpl.of("N/#"),
                        MqttQos.AT_MOST_ONCE,
                        false,
                        Mqtt5RetainHandling.SEND,
                        true
                    )
                ),
                MqttUserPropertiesImpl.NO_USER_PROPERTIES
            )
        ) {
            val body = it.payload.orElse(null)?.let {
                Charsets.UTF_8.decode(it)
            }?.toString() ?: "{}"
            val jsonRaw = objectMapper.readValue<Map<String, Any>>(body)

            synchronized(this) {
                essValues = essValues.update(
                    getPortalId(),
                    it.topic.toString(),
                    jsonRaw
                )
                if (lastNotify < (System.currentTimeMillis() - delayInMs)) {
                    lastNotify = System.currentTimeMillis()
                    logger.info { "sending notify $essValues listeners=${listeners.size}" }
                    executorService.submit {
                        val c = essValues
                        influxDBClient.recordSensorData(
                            c.toInfluxDBRecord()
                        )
                    }
                    executorService.submit {
                        listeners.forEach {
                            it.value(essValues)
                        }
                    }
                }
            }
        }.get()

        scheduledExecutorService.scheduleAtFixedRate({
            executorService.submit {
                send(topic(TopicType.read, "/system/0/Serial"))
                send(topic(TopicType.read, "/system/0/SystemState/State"))

                send(topic(TopicType.read, "/vebus/276/Hub4/L1/AcPowerSetpoint"))
                send(topic(TopicType.read, "/vebus/276/Hub4/L2/AcPowerSetpoint"))
                send(topic(TopicType.read, "/vebus/276/Hub4/L3/AcPowerSetpoint"))
            }
        }, delayInMs, delayInMs, TimeUnit.MILLISECONDS)
    }

    private fun getPortalId() = persistenceService["VictronService.portalId"] ?: error("VictronService.portalId not configured")

    fun current() = essValues

    fun writeEnabled() = persistenceService["VictronService.writeEnabled", "false"]!! == "true"

    fun essMode3_setAcPowerSetPointMode(acSetPoints: VictronEssCalculation.VictronEssCalculationResult?) {
        if (acSetPoints == null) {
            // passthrough
            send(topic(TopicType.write, "/vebus/276/Hub4/DisableFeedIn"), 1)
            send(topic(TopicType.write, "/vebus/276/Hub4/DisableCharge"), 1)
            send(topic(TopicType.write, "/vebus/276/Hub4/L1/AcPowerSetpoint"), 0)
            send(topic(TopicType.write, "/vebus/276/Hub4/L2/AcPowerSetpoint"), 0)
            send(topic(TopicType.write, "/vebus/276/Hub4/L3/AcPowerSetpoint"), 0)
        } else {
            send(topic(TopicType.write, "/vebus/276/Hub4/DisableFeedIn"), 0)
            send(topic(TopicType.write, "/vebus/276/Hub4/DisableCharge"), 0)
            send(topic(TopicType.write, "/vebus/276/Hub4/L1/AcPowerSetpoint"), acSetPoints.acPowerSetPointL1)
            send(topic(TopicType.write, "/vebus/276/Hub4/L2/AcPowerSetpoint"), acSetPoints.acPowerSetPointL2)
            send(topic(TopicType.write, "/vebus/276/Hub4/L3/AcPowerSetpoint"), acSetPoints.acPowerSetPointL3)
        }
    }

    fun isGridOk() = essValues.isGridOk()

    fun send(topic: String) {
        sendAny(topic, null)
    }

    fun send(topic: String, value: Long) {
        sendAny(topic, mapOf("value" to value))
    }

    private fun sendAny(topic: String, value: Any?) {
        if (topic.startsWith("W/") && !writeEnabled()) {
            logger.warn { "Could not publish to Victron - write disabled" }
            return
        }

        val msg = value?.let { objectMapper.writeValueAsBytes(it) }
        asyncClient.publish(
            MqttWillPublish(
                MqttTopicImpl.of(topic),
                msg?.let { ByteBuffer.wrap(it) },
                MqttQos.AT_MOST_ONCE,
                false,
                1000,
                null,
                null,
                null,
                null,
                MqttUserPropertiesImpl.NO_USER_PROPERTIES,
                0
            )
        ).get()
    }

    fun topic(type: TopicType, path: String) = when (type) {
        TopicType.notify -> "N"
        TopicType.read -> "R"
        TopicType.write -> "W"
    } + "/${getPortalId()}$path"


}

// https://github.com/victronenergy/venus-html5-app/blob/master/TOPICS.md
enum class TopicType {
    notify,
    read,
    write
}

fun doubleValue(any: Any?) = when {
    any == null -> 0.0
    any is Int -> any.toDouble()
    any is Long -> any.toDouble()
    any is Double -> any
    else -> {
        VictronService.logger.error { "doubleValue - Unsupported type $any" }
        0.0
    }
}

fun booleanValue(any: Any?) = when {
    any == null -> false
    any is Int -> any != 0
    any is Long -> any != 0L
    any is Double -> any.toLong() != 0L
    else -> {
        VictronService.logger.error { "booleanValue - Unsupported type $any" }
        false
    }
}

fun intValue(any: Any?) =
    when {
        any == null -> 0
        any is Int -> any
        any is Long -> any.toInt()
        any is Double -> any.toInt()
        else -> {
            VictronService.logger.error { "intValue - Unsupported type $any" }
            0
        }
    }

fun longValue(any: Any?) =
    when {
        any == null -> 0L
        any is Int -> any.toLong()
        any is Long -> any
        any is Double -> any.toLong()
        else -> {
            VictronService.logger.error { "longValue - Unsupported type $any" }
            0L
        }
    }


data class ESSValues(
    val soc: Double = 0.0, // /system/0/Dc/Battery/Soc
    val batteryCurrent: Double = 0.0, // /system/0/Dc/Vebus/Current
    val batteryPower: Double = 0.0,   // /system/0/Dc/Battery/Power
    val batteryVoltage: Double = 0.0, // /system/0/Dc/Battery/Voltage

    val gridL1: GridData = GridData(), // /vebus/276/Ac/ActiveIn/L1/F
    val gridL2: GridData = GridData(),
    val gridL3: GridData = GridData(),
    val gridPower: Double = 0.0, // /vebus/276/Ac/ActiveIn/P

    val outputL1: GridData = GridData(), // /vebus/276/Ac/Out/L1/F
    val outputL2: GridData = GridData(),
    val outputL3: GridData = GridData(),
    val outputPower: Double = 0.0, // /vebus/276/Ac/Out/P

    val systemState: SystemState = SystemState.Off, // /system/0/SystemState/State
    val mode: Mode = Mode.Off, // /vebus/276/Mode

    val inverterAlarms: List<InverterAlarms> = emptyList(),
    val batteryAlarm: Int = 0,
    val batteryAlarms: List<BatteryAlarms> = emptyList(),
) {

    fun toInfluxDBRecord() = InfluxDBRecord(
        Instant.now(),
        "energyStorageSystem",
        mapOf(
            "baterySoC" to soc.toInt().toString(),
            "batteryCurrent" to batteryCurrent.toString(),
            "batteryPower" to batteryPower.toString(),
            "batteryVoltage" to batteryVoltage.toString(),
            "gridL1Power" to gridL1.power.toString(),
            "gridL2Power" to gridL2.power.toString(),
            "gridL3Power" to gridL3.power.toString(),
            "gridPower" to gridPower.toString(),
            "outputL1Power" to outputL1.power.toString(),
            "outputL2Power" to outputL2.power.toString(),
            "outputL3Power" to outputL3.power.toString(),
            "outputPower" to outputPower.toString(),
        ),
        mapOf()
    )

    fun isGridOk() = listOf(
        gridL1,
        gridL2,
        gridL3,
    ).all { it.voltage > 200 } && (InverterAlarms.GridLost !in inverterAlarms)

    fun update(portalId: String, topicIn: String, json: Map<String, Any>): ESSValues {
        val topic = topicIn.replace("N/$portalId", "")

        val any = json["value"]

        return when (topic) {
            "/system/0/Dc/Battery/Soc" -> this.copy(soc = doubleValue(any))

            "/system/0/Dc/Battery/Current" -> this.copy(batteryCurrent = doubleValue(any))
            "/system/0/Dc/Battery/Power" -> this.copy(batteryPower = doubleValue(any))
            "/system/0/Dc/Battery/Voltage" -> this.copy(batteryVoltage = doubleValue(any))
            "/vebus/276/Ac/ActiveIn/P" -> this.copy(gridPower = doubleValue(any))
            "/vebus/276/Ac/Out/P" -> this.copy(outputPower = doubleValue(any))
            "/system/0/SystemState/State" -> {
                val v = intValue(any)
                val systemState1 = SystemState.values().firstOrNull { it.value == v } ?: SystemState.Unknown
                this.copy(systemState = systemState1)
            }

            "/vebus/276/Mode" -> {
                val v = intValue(any)
                this.copy(mode = Mode.values().firstOrNull { it.value == v } ?: Mode.Unknown)
            }

            "/battery/0/Alarms/Alarm" -> this.copy(batteryAlarm = intValue(any))

            else -> {
                when {
                    topicIn.contains("/vebus/276/Ac/ActiveIn/L1") -> this.copy(gridL1 = gridL1.update(portalId, topicIn, json))
                    topicIn.contains("/vebus/276/Ac/ActiveIn/L2") -> this.copy(gridL2 = gridL2.update(portalId, topicIn, json))
                    topicIn.contains("/vebus/276/Ac/ActiveIn/L3") -> this.copy(gridL3 = gridL3.update(portalId, topicIn, json))
                    topicIn.contains("/vebus/276/Ac/Out/L1") -> this.copy(outputL1 = outputL1.update(portalId, topicIn, json))
                    topicIn.contains("/vebus/276/Ac/Out/L2") -> this.copy(outputL2 = outputL2.update(portalId, topicIn, json))
                    topicIn.contains("/vebus/276/Ac/Out/L3") -> this.copy(outputL3 = outputL3.update(portalId, topicIn, json))
                    topicIn.contains("/vebus/276/Alarms") -> this.updateInverterAlarms(portalId, topicIn, json)
                    topicIn.contains("/battery/0/Alarms") -> this.updateBatteryAlarms(portalId, topicIn, json)
                    else -> {
                        this
                    }
                }
            }
        }
    }

    private fun updateInverterAlarms(portalId: String, topicIn: String, json: Map<String, Any>): ESSValues {
        val topic = topicIn.replace("N/$portalId", "")
        val any = json["value"]
        val (alarm, triggered) = when (topic) {
            "/vebus/276/Alarms/GridLost" -> InverterAlarms.GridLost to booleanValue(any)
            "/vebus/276/Alarms/HighTemperature" -> InverterAlarms.HighTemperature to booleanValue(any)
            "/vebus/276/Alarms/LowBattery" -> InverterAlarms.LowBattery to booleanValue(any)
            "/vebus/276/Alarms/Overload" -> InverterAlarms.Overload to booleanValue(any)
            "/vebus/276/Alarms/Ripple" -> InverterAlarms.Ripple to booleanValue(any)
            "/vebus/276/Alarms/TemperatureSensor" -> InverterAlarms.TemperatureSensor to booleanValue(any)
            else -> return this
        }
        return if (triggered) {
            this.copy(inverterAlarms = (this.inverterAlarms + alarm).distinct().sorted())
        } else {
            this.copy(inverterAlarms = this.inverterAlarms.filterNot { it == alarm })
        }
    }

    private fun updateBatteryAlarms(portalId: String, topicIn: String, json: Map<String, Any>): ESSValues {
        val topic = topicIn.replace("N/$portalId", "")
        val any = json["value"]
        val (alarm, triggered) = when (topic) {
            "/battery/0/Alarms/FuseBlown" -> BatteryAlarms.FuseBlown to booleanValue(any)
            "/battery/0/Alarms/HighInternalTemperature" -> BatteryAlarms.HighInternalTemperature to booleanValue(any)
            "/battery/0/Alarms/HighTemperature" -> BatteryAlarms.HighTemperature to booleanValue(any)
            "/battery/0/Alarms/HighVoltage" -> BatteryAlarms.HighVoltage to booleanValue(any)
            "/battery/0/Alarms/LowSoc" -> BatteryAlarms.LowSoc to booleanValue(any)
            "/battery/0/Alarms/LowTemperature" -> BatteryAlarms.LowTemperature to booleanValue(any)
            "/battery/0/Alarms/LowVoltage" -> BatteryAlarms.LowVoltage to booleanValue(any)
            else -> return this
        }
        return if (triggered) {
            this.copy(batteryAlarms = (this.batteryAlarms + alarm).distinct().sorted())
        } else {
            this.copy(batteryAlarms = this.batteryAlarms.filterNot { it == alarm })
        }
    }

}

enum class BatteryAlarms {
    FuseBlown,
    HighInternalTemperature,
    HighTemperature,
    HighVoltage,
    LowSoc,
    LowTemperature,
    LowVoltage,
}

enum class InverterAlarms {
    GridLost,
    HighTemperature,
    LowBattery,
    Overload,
    Ripple,
    TemperatureSensor,
}

enum class Mode(val value: Int) {
    Unknown(-1),
    ChargerOnly(1),
    InverterOnly(2),
    On(3),
    Off(4),
}

enum class SystemState(
    val value: Int,
    val text: String
) {
    Unknown(-1, "Unknown"),
    Off(0, "Off"),
    VeBusFault(2, "VE.Bus Fault condition"),
    BulkCharging(3, "Bulk charging"),
    AbsorptionCharging(4, "Absorption charging"),
    FloatCharging(5, "Float charging"),
    StorageMode(6, "Storage mode"),
    EqChargning(7, "Equalisation charging"),
    Passthru(8, "Passthru"),
    Inverting(9, "Inverting"),
    Assisting(10, "Assisting"),
    Discharging(256, "Discharging"),
    Sustain(257, "Sustain"),
}

data class GridData(
    val current: Double = 0.0, // N/48e7da87e605/vebus/276/Ac/ActiveIn/L1/I
    val power: Double = 0.0, // N/48e7da87e605/vebus/276/Ac/ActiveIn/L1/P
    val freq: Double = 0.0, // N/48e7da87e605/vebus/276/Ac/ActiveIn/L1/F
    val voltage: Double = 0.0, // N/48e7da87e605/vebus/276/Ac/ActiveIn/L1/V
) {
    fun update(portalId: String, topicIn: String, json: Map<String, Any>): GridData {
        var topic = topicIn.replace("N/$portalId/vebus/276/Ac/ActiveIn/L1", "")
        topic = topic.replace("N/$portalId/vebus/276/Ac/ActiveIn/L2", "")
        topic = topic.replace("N/$portalId/vebus/276/Ac/ActiveIn/L3", "")
        topic = topic.replace("N/$portalId/vebus/276/Ac/Out/L1", "")
        topic = topic.replace("N/$portalId/vebus/276/Ac/Out/L2", "")
        topic = topic.replace("N/$portalId/vebus/276/Ac/Out/L3", "")

        val any = json["value"]

        return when (topic) {
            "/I" -> this.copy(current = doubleValue(any))
            "/S" -> this
            "/P" -> this.copy(power = doubleValue(any))
            "/F" -> this.copy(freq = doubleValue(any))
            "/V" -> this.copy(voltage = doubleValue(any))
            else -> {
                println("Ignoring $topicIn")
                this
            }
        }
    }
}
