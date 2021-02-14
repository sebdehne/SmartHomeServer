package com.dehnes.smarthome.api

import com.dehnes.smarthome.api.RequestType.*
import com.dehnes.smarthome.configuration
import com.dehnes.smarthome.service.GarageDoorService
import com.dehnes.smarthome.service.UnderFloorHeaterService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import java.io.Closeable
import java.util.*
import javax.websocket.*
import javax.websocket.server.ServerEndpoint

// one instance per sessions
@ServerEndpoint(value = "/api")
class WebSocketServer {

    private val objectMapper = configuration.getBean<ObjectMapper>(ObjectMapper::class)
    private val logger = KotlinLogging.logger { }
    private val garageDoorService = configuration.getBean<GarageDoorService>(GarageDoorService::class)
    private val underFloopHeaterService = configuration.getBean<UnderFloorHeaterService>(UnderFloorHeaterService::class)
    private val subscriptions = mutableMapOf<String, Subscription<*>>()

    @OnOpen
    fun onWebSocketConnect(sess: Session) {
        logger.info("Socket connected: $sess")
    }

    @OnMessage
    fun onWebSocketText(argSession: Session, argMessage: String) {
        val websocketMessage: WebsocketMessage = objectMapper.readValue(argMessage)

        if (websocketMessage.type != WebsocketMessageType.rpcRequest) {
            return
        }

        val rpcRequest = websocketMessage.rpcRequest!!
        val response: RpcResponse = when (rpcRequest.type) {
            updateUnderFloorHeaterMode -> RpcResponse(updateUnderFloorHeaterModeSuccess = underFloopHeaterService.update(rpcRequest.updateUnderFloorHeaterMode!!))
            getUnderFloorHeaterStatus -> RpcResponse(underFloorHeaterStatus = underFloopHeaterService.getCurrentState())
            openGarageDoor -> RpcResponse(garageCommandSendSuccess = garageDoorService.sendCloseCommand())
            closeGarageDoor -> RpcResponse(garageCommandSendSuccess = garageDoorService.sendCloseCommand())
            getGarageStatus -> RpcResponse(garageStatus = garageDoorService.getCurrentState())
            subscribe -> {
                val subscribe = rpcRequest.subscribe!!
                val subscriptionId = subscribe.subscriptionId

                val sub = when (subscribe.type) {
                    getGarageStatus -> GarageStatusSubscription(subscriptionId, argSession).apply {
                        garageDoorService.listeners[subscriptionId] = this::onEvent
                    }
                    getUnderFloorHeaterStatus -> UnderFloorHeaterSubscription(subscriptionId, argSession).apply {
                        underFloopHeaterService.listeners[subscriptionId] = this::onEvent
                    }
                    else -> error("Not supported subscription ${subscribe.type}")
                }

                subscriptions.put(subscriptionId, sub)?.close()
                logger.info { "New subscription id=$subscriptionId type=${subscribe.type}" }
                RpcResponse(subscriptionCreated = true)
            }
            unsubscribe -> {
                val subscriptionId = rpcRequest.unsubscribe!!.subscriptionId
                subscriptions.remove(subscriptionId)?.close()
                logger.info { "Removed subscription id=$subscriptionId" }
                RpcResponse(subscriptionRemoved = true)
            }
        }

        argSession.basicRemote.sendText(
            objectMapper.writeValueAsString(
                WebsocketMessage(
                    websocketMessage.id,
                    WebsocketMessageType.rpcResponse,
                    null,
                    response,
                    null
                )
            )
        )
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
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(subscriptionId, e, null)
                    )
                )
            )
        }

        override fun close() {
            garageDoorService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }

    inner class UnderFloorHeaterSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<UnderFloorHeaterStatus>(subscriptionId, sess) {
        override fun onEvent(e: UnderFloorHeaterStatus) {
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(subscriptionId, null, e)
                    )
                )
            )
        }

        override fun close() {
            underFloopHeaterService.listeners.remove(subscriptionId)
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

