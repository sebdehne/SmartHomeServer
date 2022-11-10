package com.dehnes.smarthome.victron

import com.dehnes.smarthome.api.dtos.EssRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class VictronService(
    victronHost: String,
    private val objectMapper: ObjectMapper,
    private val executorService: ExecutorService
) {

    companion object {
        val logger = KotlinLogging.logger { }
    }

    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    val asyncClient = MqttClient.builder()
        .identifier(UUID.randomUUID().toString())
        .serverAddress(InetSocketAddress(victronHost, 1883))
        .useMqttVersion5()
        .automaticReconnectWithDefaultConfig()
        .buildAsync()

    val listeners = ConcurrentHashMap<String, (data: ESSValues) -> Unit>()

    var essValues = ESSValues()
    var lastNotify = System.currentTimeMillis()
    val delayInMs = 20 * 1000L

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
                    it.topic.toString(),
                    jsonRaw
                )
                if (lastNotify < (System.currentTimeMillis() - delayInMs)) {
                    lastNotify = System.currentTimeMillis()
                    logger.info { "sending notify $essValues listeners=${listeners.size}" }
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
                send(topic(TopicType.read, "/settings/0/Settings/CGwacs/AcPowerSetPoint"))
                send(topic(TopicType.read, "/settings/0/Settings/CGwacs/MaxChargePower"))
                send(topic(TopicType.read, "/settings/0/Settings/CGwacs/MaxDischargePower"))
            }
        }, delayInMs, delayInMs, TimeUnit.MILLISECONDS)
    }

    fun current() = essValues

    fun handleWrite(req: EssRequest) {
        if (req.acPowerSetPoint != null) {
            setAcPowerSetPoint(req.acPowerSetPoint)
        }
        if (req.maxChargePower != null) {
            setMaxChargePower(req.maxChargePower)
        }
        if (req.maxDischargePower != null) {
            setMaxDischargePower(req.maxDischargePower)
        }
    }

    fun setAcPowerSetPoint(value: Long) {
        send(topic(TopicType.write, "/settings/0/Settings/CGwacs/AcPowerSetPoint"), value)
    }

    fun setMaxChargePower(value: Long) {
        send(topic(TopicType.write, "/settings/0/Settings/CGwacs/MaxChargePower"), value)
    }

    fun setMaxDischargePower(value: Long) {
        send(topic(TopicType.write, "/settings/0/Settings/CGwacs/MaxDischargePower"), value)
    }

    fun send(topic: String) {
        sendAny(topic, null)
    }

    fun send(topic: String, value: Long) {
        sendAny(
            topic, mapOf(
                "value" to value
            )
        )
    }

    fun send(topic: String, topics: List<String>) {
        sendAny(topic, topics)
    }

    private fun sendAny(topic: String, value: Any?) {

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

}

interface Profile {
    val profileSettings: ProfileSettings
}


// Energy management
// EV Charger 1:
// EV Charger 2:
// ESS:
// Heater under floor:


// auto:
// - charge (if prices are cheap && SoC < max)
// - discharge (if prices are !OK && SoC > min)
// - else: passthrough
// passthrough / stop
// manual:
//

data class ProfileSettings(
    val acPowerSetPoint: Long = 0, // /settings/0/Settings/CGwacs/AcPowerSetPoint
    val maxChargePower: Long = 0, // /settings/0/Settings/CGwacs/MaxChargePower
    val maxDischargePower: Long = 0, // /settings/0/Settings/CGwacs/MaxDischargePower
)

// https://github.com/victronenergy/venus-html5-app/blob/master/TOPICS.md
const val portalId = "48e7da87e605"

enum class TopicType {
    notify,
    read,
    write
}

fun topic(type: TopicType, path: String) = when (type) {
    TopicType.notify -> "N"
    TopicType.read -> "R"
    TopicType.write -> "W"
} + "/$portalId$path"

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

    val systemState: SystemState = SystemState.Off, // /vebus/276/State
    val mode: Mode = Mode.Off, // /vebus/276/Mode

    val acPowerSetPoint: Long = 0, // /settings/0/Settings/CGwacs/AcPowerSetPoint
    val maxChargePower: Long = 0, // /settings/0/Settings/CGwacs/MaxChargePower
    val maxDischargePower: Long = 0, // /settings/0/Settings/CGwacs/MaxDischargePower

) {
    fun update(topicIn: String, json: Map<String, Any>): ESSValues {
        val topic = topicIn.replace("N/$portalId", "")

        val any = json["value"]

        return when (topic) {
            "/system/0/Dc/Battery/Soc" -> this.copy(soc = doubleValue(any))

            "/settings/0/Settings/CGwacs/AcPowerSetPoint" -> this.copy(acPowerSetPoint = longValue(any))
            "/settings/0/Settings/CGwacs/MaxChargePower" -> this.copy(maxChargePower = longValue(any))
            "/settings/0/Settings/CGwacs/MaxDischargePower" -> this.copy(maxDischargePower = longValue(any))


            "/system/0/Dc/Battery/Current" -> this.copy(batteryCurrent = doubleValue(any))
            "/system/0/Dc/Battery/Power" -> this.copy(batteryPower = doubleValue(any))
            "/system/0/Dc/Battery/Voltage" -> this.copy(batteryVoltage = doubleValue(any))
            "/vebus/276/Ac/ActiveIn/P" -> this.copy(gridPower = doubleValue(any))
            "/vebus/276/Ac/Out/P" -> this.copy(outputPower = doubleValue(any))
            "/system/0/SystemState/State" -> {
                val v = intValue(any)
                this.copy(systemState = SystemState.values().firstOrNull {
                    it.value == v
                } ?: SystemState.Unknown)
            }

            "/vebus/276/Mode" -> {
                val v = intValue(any)
                this.copy(mode = Mode.values().firstOrNull { it.value == v } ?: Mode.Unknown)
            }

            else -> {
                when {
                    topicIn.contains("/vebus/276/Ac/ActiveIn/L1") -> this.copy(gridL1 = gridL1.update(topicIn, json))
                    topicIn.contains("/vebus/276/Ac/ActiveIn/L2") -> this.copy(gridL2 = gridL2.update(topicIn, json))
                    topicIn.contains("/vebus/276/Ac/ActiveIn/L3") -> this.copy(gridL3 = gridL3.update(topicIn, json))
                    topicIn.contains("/vebus/276/Ac/Out/L1") -> this.copy(outputL1 = outputL1.update(topicIn, json))
                    topicIn.contains("/vebus/276/Ac/Out/L2") -> this.copy(outputL2 = outputL2.update(topicIn, json))
                    topicIn.contains("/vebus/276/Ac/Out/L3") -> this.copy(outputL3 = outputL3.update(topicIn, json))
                    else -> {
                        this
                    }
                }
            }
        }
    }
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
    fun update(topicIn: String, json: Map<String, Any>): GridData {
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


fun main() {
    val objectMapper = jacksonObjectMapper().registerModule(kotlinModule())
    val victronService = VictronService("192.168.1.18", objectMapper, Executors.newCachedThreadPool())

    victronService.listeners["me"] = {
        println(it)
    }

    Thread.sleep(50000)

    victronService.asyncClient.disconnect().get()

    if (true) return

    //victronService.send(topic(TopicType.write, "/settings/0/Settings/CGwacs/AcPowerSetPoint"), 0)

    while (true) {

        victronService.send(topic(TopicType.read, "/system/0/Serial"))
//        victronService.send("R/48e7da87e605/system/0/Ac/Grid/L1/Power", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/Grid/L1/Current", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/Grid/L2/Power", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/Grid/L2/Current", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/Grid/L3/Power", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/Grid/L3/Current", 2)

//        victronService.send("R/48e7da87e605/system/0/Dc/Battery/Current", 2)
//        victronService.send("R/48e7da87e605/system/0/Dc/Battery/Power", 2)

//        victronService.send("R/48e7da87e605/battery/0/Dc/0/Current", 2)
//        victronService.send("R/48e7da87e605/battery/0/Dc/0/Power", 2)
//        victronService.send("R/48e7da87e605/battery/0/Dc/0/Voltage", 2)
//
//        victronService.send("R/48e7da87e605/system/0/Ac/ConsumptionOnOutput/L1/Current", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/ConsumptionOnOutput/L1/Power", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/ConsumptionOnOutput/L2/Current", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/ConsumptionOnOutput/L2/Power", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/ConsumptionOnOutput/L3/Current", 2)
//        victronService.send("R/48e7da87e605/system/0/Ac/ConsumptionOnOutput/L3/Power", 2)
    }

    victronService.send("W/48e7da87e605/settings/0/Settings/CGwacs/Hub4Mode", 2)
    victronService.send("W/48e7da87e605/settings/0/Settings/CGwacs/MaxDischargePower", -1)
    victronService.send("W/48e7da87e605/settings/0/Settings/CGwacs/MaxChargePower", -1)
    victronService.send("W/48e7da87e605/settings/0/Settings/CGwacs/AcPowerSetPoint", 1000)


    // W/48e7da87e605/settings/0/Settings/CGwacs/MaxDischargePower
    // W/48e7da87e605/settings/0/Settings/CGwacs/MaxChargePower
    // W/48e7da87e605/settings/0/Settings/CGwacs/AcPowerSetPoint

}