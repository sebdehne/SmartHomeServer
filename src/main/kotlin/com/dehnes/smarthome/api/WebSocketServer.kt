package com.dehnes.smarthome.api

import com.dehnes.smarthome.configuration
import com.dehnes.smarthome.service.GarageDoorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import java.io.Closeable
import javax.websocket.*
import javax.websocket.server.ServerEndpoint

// one instance per sessions
@ServerEndpoint(value = "/api")
class WebSocketServer {

    private val objectMapper = configuration.getBean<ObjectMapper>(ObjectMapper::class)
    private val logger = KotlinLogging.logger { }
    private val garageDoorService = configuration.getBean<GarageDoorService>(GarageDoorService::class)
    private val subscriptions = mutableMapOf<String, Subscription<*>>()

    @OnOpen
    fun onWebSocketConnect(sess: Session) {
        logger.info("Socket connected: $sess")
    }

    @OnMessage
    fun onWebSocketText(argSession: Session, argMessage: String) {
        val apiRequest: ApiRequest = objectMapper.readValue(argMessage)

        val response: Any? = when (apiRequest.type) {
            RequestType.getGarageStatus -> garageDoorService.getCurrentState()
            RequestType.subscribeGarageStatus -> {
                val subscriptionId = apiRequest.subscriptionId!!
                val sub = GarageStatusSubscription(subscriptionId, argSession)
                garageDoorService.listeners[subscriptionId] = sub::onEvent
                subscriptions.put(subscriptionId, sub)?.close()
                Notify(subscriptionId, garageDoorService.getCurrentState())
                logger.info { "New subscribeGarageStatus id=$subscriptionId" }
            }
            RequestType.unsubscribeGarageStatus -> {
                val subscriptionId = apiRequest.subscriptionId!!
                subscriptions[subscriptionId]!!.close()
                Notify(subscriptionId, garageDoorService.getCurrentState())
                logger.info { "Removed subscribeGarageStatus id=$subscriptionId" }
            }
        }

        argSession.basicRemote.sendText(objectMapper.writeValueAsString(ApiResponse(response)))
    }

    @OnClose
    fun onWebSocketClose(reason: CloseReason) {
        subscriptions.forEach { (_, u) -> u.close() }
        logger.info("Socket Closed: $reason")
    }

    @OnError
    fun onWebSocketError(cause: Throwable) {
        logger.warn("", cause)
    }

    inner class GarageStatusSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<GarageStatus>(subscriptionId, sess) {
        override fun onEvent(e: GarageStatus) {
            sess.basicRemote.sendText(objectMapper.writeValueAsString(Notify(subscriptionId, e)))
        }

        override fun close() {
            garageDoorService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }
}

abstract class Subscription<E>(
    val subscriptionId: String,
    val sess: Session
) : Closeable {
    abstract fun onEvent(e: E)
}

