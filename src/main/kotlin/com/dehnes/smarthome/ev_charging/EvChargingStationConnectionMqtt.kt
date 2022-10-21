package com.dehnes.smarthome.ev_charging

import com.fasterxml.jackson.databind.ObjectMapper
import com.hivemq.client.internal.mqtt.datatypes.MqttTopicImpl
import com.hivemq.client.internal.mqtt.datatypes.MqttUserPropertiesImpl
import com.hivemq.client.internal.mqtt.message.publish.MqttWillPublish
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.TimeUnit

class EvChargingStationConnectionMqtt(
    private val mqttBrokerHost: String,
    private val mqttBrokerPort: Int,
    private val objectMapper: ObjectMapper,
    private val topicToEvse: String,
    private val topicFromEvse: String,
) {

    fun start() {
        val asyncClient = MqttClient.builder()
            .identifier(UUID.randomUUID().toString())
            .serverAddress(InetSocketAddress(mqttBrokerHost, mqttBrokerPort))
            .useMqttVersion5()
            .automaticReconnectWithDefaultConfig()
            .buildAsync()

        asyncClient.connect().get(20, TimeUnit.SECONDS)

        val mqttMsg = EvseMqttMessage(
            EvseMqttMessageType.request_ping,
            "Charger 1"
        )

        val payload = ByteBuffer.wrap(objectMapper.writeValueAsBytes(mqttMsg))

        val publish = asyncClient.publish(
            MqttWillPublish(
                MqttTopicImpl.of("to_dehneEVSE"),
                payload,
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

    fun stop() {

    }

}

enum class EvseMqttMessageType {
    new_connection,
    notify,

    response_ping,
    response_collect_data,
    response_set_pwm_percent,
    response_set_contactor_state,

    request_ping,
    request_data_collection,
    request_firmware,
    request_set_pwm_percent,
    request_set_contactor_state,
}
data class EvseMqttMessage(
    val message_type: EvseMqttMessageType,
    val client_id: String,
    val firmware: Int? = null,
    val pwm_percent: Int? = null,
    val contactor_state: Boolean? = null,
    val measurements: EvseMqttMeasurements? = null
)

enum class EvseMqttPilotVoltage {
    volt_12,
    volt_9,
    volt_6,
    volt_3,
    fault,
}

enum class EvseMqttProximityPilotAmps {
    amp_13,
    amp_20,
    amp_32,
    no_cable,
}

data class EvseMqttMeasurements(
    val pilot_voltage: EvseMqttPilotVoltage,
    val proximity_pilot_amps: EvseMqttProximityPilotAmps,
    val phase1_millivolts: Long,
    val phase2_millivolts: Long,
    val phase3_millivolts: Long,
    val phase1_milliamps: Long,
    val phase2_milliamps: Long,
    val phase3_milliamps: Long,
    val wifi_rssi: Long,
    val uptime_milliseconds: Long,
    val current_control_pilot_adc: Long,
    val current_proximity_pilot_adc: Long,
    val logging_buffer: String,
)