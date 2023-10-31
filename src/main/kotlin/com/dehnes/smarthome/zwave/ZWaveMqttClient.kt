package com.dehnes.smarthome.zwave

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
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock


class Listener(
    val id: String,
    val topic: String,
    val fn: (String, Map<String, Any>) -> Unit,
)

class ZWaveMqttClient(
    mqttHost: String,
    private val objectMapper: ObjectMapper,
    private val executorService: ExecutorService,
    private val reconnectOnSilenceFor: Duration = Duration.ofMinutes(5),
    private val prefix: String = "zwave"
) {

    private var lastMessageReceivedAt: Instant? = null

    private val logger = KotlinLogging.logger { }
    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    val listeners = ConcurrentHashMap<String, Listener>()

    val asyncClient = MqttClient.builder()
        .identifier(UUID.randomUUID().toString())
        .serverAddress(InetSocketAddress(mqttHost, 1883))
        .useMqttVersion5()
        .automaticReconnect()
        .initialDelay(1, TimeUnit.SECONDS)
        .maxDelay(5, TimeUnit.SECONDS)
        .applyAutomaticReconnect()
        .addConnectedListener { resubscribe() }
        .buildAsync()

    private val lock = ReentrantLock()
    val delayInMs = 60 * 1000L

    init {
        scheduledExecutorService.scheduleAtFixedRate({
            executorService.submit(withLogging {

                lock.tryLock()
                try {
                    if (lastMessageReceivedAt == null || lastMessageReceivedAt!!.isBefore(
                            Instant.now().minusMillis(reconnectOnSilenceFor.toMillis())
                        )
                    ) {
                        logger.warn { "Need to re-connect. lastMessageReceivedAt=$lastMessageReceivedAt" }
                        reconnect(force = true)
                    }
                } finally {
                    lock.unlock()
                }
            })
        }, 0, delayInMs, TimeUnit.MILLISECONDS)
    }

    private fun resubscribe() {
        logger.info { "Re-subscribing..." }
        listeners.forEach { (_, l) ->
            subscribe(l)
        }
    }

    private fun subscribe(l: Listener) {
        asyncClient.subscribe(
            MqttSubscribe(
                ImmutableList.of(
                    MqttSubscription(
                        MqttTopicFilterImpl.of("${prefix}/${l.topic}"),
                        MqttQos.AT_MOST_ONCE,
                        false,
                        Mqtt5RetainHandling.SEND,
                        true
                    )
                ),
                MqttUserPropertiesImpl.NO_USER_PROPERTIES
            ), onMqttMessage(l)
        )
    }

    fun addListener(l: Listener) {
        listeners[l.id] = l
        subscribe(l)
    }

    private fun reconnect(force: Boolean = false) {
        if (force && asyncClient.state != MqttClientState.DISCONNECTED) {
            logger.warn { "Forced disconnect" }
            asyncClient.disconnect().get(20, TimeUnit.SECONDS)
        }
        if (asyncClient.state == MqttClientState.DISCONNECTED) {
            logger.info { "(re-)connecting..." }
            asyncClient.connect().get(20, TimeUnit.SECONDS)
        }
    }

    fun sendAny(topic: String, value: Any?) {
        reconnect()

        val msg = value?.let { objectMapper.writeValueAsBytes(it) }
        asyncClient.publish(
            MqttWillPublish(
                MqttTopicImpl.of("$prefix/$topic"),
                msg?.let { ByteBuffer.wrap(it) },
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

    private fun onMqttMessage(l: Listener) = { msg: Mqtt5Publish ->
        val body = msg.payload.orElse(null)?.let {
            Charsets.UTF_8.decode(it)
        }?.toString() ?: "{}"
        val jsonRaw = objectMapper.readValue<Map<String, Any>>(body)

        synchronized(this) {
            lastMessageReceivedAt = Instant.now()
        }

        try {
            val topic = msg.topic.toString()
            l.fn(topic.substring(prefix.length), jsonRaw)
        } catch (e: Exception) {
            logger.error(e) { "" }
        }
    }


}