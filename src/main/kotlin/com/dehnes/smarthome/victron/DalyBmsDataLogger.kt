package com.dehnes.smarthome.victron

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.toInt
import com.dehnes.smarthome.utils.withLogging
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
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class DalyBmsDataLogger(
    private val influxDBClient: InfluxDBClient,
    private val objectMapper: ObjectMapper,
    victronHost: String,
    private val executorService: ExecutorService,
    private val userSettingsService: UserSettingsService,
) {

    private val asyncClient = MqttClient.builder()
        .identifier(UUID.randomUUID().toString())
        .serverAddress(InetSocketAddress(victronHost, 1883))
        .useMqttVersion5()
        .automaticReconnect()
        .initialDelay(1, TimeUnit.SECONDS)
        .maxDelay(5, TimeUnit.SECONDS)
        .applyAutomaticReconnect()
        .addConnectedListener { resubscribe() }
        .buildAsync()

    private val logger = KotlinLogging.logger { }
    private val lock = ReentrantLock()
    val listeners = ConcurrentHashMap<String, (List<BmsData>) -> Unit>()

    fun reconnect() {
        asyncClient.disconnect()
        asyncClient.connect().get(20, TimeUnit.SECONDS)
    }

    fun resubscribe() {

        asyncClient.subscribe(
            MqttSubscribe(
                ImmutableList.of(
                    MqttSubscription(
                        MqttTopicFilterImpl.of("W/dbus-mqtt-services"),
                        MqttQos.AT_MOST_ONCE,
                        false,
                        Mqtt5RetainHandling.SEND,
                        true
                    )
                ),
                MqttUserPropertiesImpl.NO_USER_PROPERTIES
            ), this::onMqttMessage
        )
    }

    fun write(user: String?, writeBms: WriteBms) {
        check(
            userSettingsService.canUserWrite(user, UserRole.energyStorageSystem)
        ) { "User $user cannot write ESS state" }
        when (writeBms.type) {
            WriteBmsType.writeSoc -> writeSoc(writeBms.soc!!, writeBms.bmsId)
        }
    }

    fun writeSoc(soc: Int, bmsId: String) {
        check(soc in 0..100)

        val msg = mapOf(
            "value" to soc
        )

        asyncClient.publish(
            MqttWillPublish(
                MqttTopicImpl.of("W/daly_bms_service/soc/$bmsId"),
                ByteBuffer.wrap(objectMapper.writeValueAsBytes(msg)),
                MqttQos.AT_LEAST_ONCE,
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

    fun onMqttMessage(msg: Mqtt5Publish) {
        val body = msg.payload.orElse(null)?.let {
            Charsets.UTF_8.decode(it)
        }?.toString() ?: "{}"
        logger.debug { "Msg received" }

        val dbusServiceMqttMessage = objectMapper.readValue<DbusServiceMqttMessage>(body)

        executorService.submit(withLogging {
            listeners.forEach { l -> l.value(dbusServiceMqttMessage.bmsData) }
        })

        executorService.submit(withLogging {
            if (lock.tryLock()) {
                try {
                    onMqttMessageLocked(dbusServiceMqttMessage)
                } finally {
                    lock.unlock()
                }
            }
        })
    }

    private fun onMqttMessageLocked(msg: DbusServiceMqttMessage) {
        val records = msg.bmsData.flatMap { bmsData ->
            bmsData.cellVoltages.mapIndexed { index, d ->
                InfluxDBRecord(
                    bmsData.timestamp,
                    "bms_data",
                    mapOf(
                        "cell_voltage" to d,
                        "soc_estimate" to bmsData.socEstimates[index]
                    ),
                    mapOf(
                        "bmsId" to bmsData.bmsId.bmsId,
                        "cell_id" to (index + 1).toString(),
                    ),
                )
            } + InfluxDBRecord(
                bmsData.timestamp,
                "bms_data",
                mapOf(
                    "voltage" to bmsData.voltage,
                    "current" to bmsData.current,
                    "soc" to bmsData.soc,
                    "avgEstimatedSoc" to bmsData.avgEstimatedSoc,
                    "maxCellVoltage" to bmsData.maxCellVoltage,
                    "maxCellNumber" to bmsData.maxCellNumber,
                    "minCellVoltage" to bmsData.minCellVoltage,
                    "minCellNumber" to bmsData.minCellNumber,
                    "maxTemp" to bmsData.maxTemp,
                    "maxTempCellNumber" to bmsData.maxTempCellNumber,
                    "minTemp" to bmsData.minTemp,
                    "minTempCellNumber" to bmsData.minTempCellNumber,
                    "status" to bmsData.status.value,
                    "mosfetCharging" to bmsData.mosfetCharging.toInt(),
                    "mosfetDischarging" to bmsData.mosfetDischarging.toInt(),
                    "lifeCycles" to bmsData.lifeCycles,
                    "remainingCapacity" to bmsData.remainingCapacity,
                    "chargerStatus" to bmsData.chargerStatus.toInt(),
                    "loadStatus" to bmsData.loadStatus.toInt(),
                    "cycles" to bmsData.cycles,
                    "errors" to bmsData.errors.size,
                ),
                mapOf("bmsId" to bmsData.bmsId.bmsId),
            )
        }

        logger.debug { "Sending ${records.size} to influxDB" }
        influxDBClient.recordSensorData(records)
    }
}

data class DbusServiceMqttMessage(
    val service: String,
    val serviceType: String,
    val serviceInstance: Int,
    val bmsData: List<BmsData>,
)

data class BmsData(
    val bmsId: BmsId,
    val timestamp: Instant,
    val voltage: Double,
    val current: Double,
    val soc: Double,
    val avgEstimatedSoc: Double,

    val maxCellVoltage: Double,
    val maxCellNumber: Int,
    val minCellVoltage: Double,
    val minCellNumber: Int,

    val maxTemp: Int,
    val maxTempCellNumber: Int,
    val minTemp: Int,
    val minTempCellNumber: Int,

    val status: BmStatus,
    val mosfetCharging: Boolean,
    val mosfetDischarging: Boolean,

    val lifeCycles: Int,
    val remainingCapacity: Double, // in Ah

    val chargerStatus: Boolean,
    val loadStatus: Boolean,

    val cycles: Int,

    val cellVoltages: List<Double>,
    val socEstimates: List<Double>,
    val errors: List<String>,
)

enum class BmStatus(val value: Int) {
    stationary(0),
    charged(1),
    discharged(2)
}

data class BmsId(
    val usbId: String,
    val bmsId: String,
    val displayName: String,
    val capacity: Int,
)

enum class WriteBmsType {
    writeSoc
}

data class WriteBms(
    val type: WriteBmsType,
    val bmsId: String,
    val soc: Int? = null,
)