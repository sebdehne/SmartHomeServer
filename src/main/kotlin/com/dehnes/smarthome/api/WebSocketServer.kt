package com.dehnes.smarthome.api

import com.dehnes.smarthome.api.RequestType.*
import com.dehnes.smarthome.configuration
import com.dehnes.smarthome.external.EVChargingStationConnection
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

    private val instanceId = UUID.randomUUID().toString()

    private val objectMapper = configuration.getBean<ObjectMapper>(ObjectMapper::class)
    private val logger = KotlinLogging.logger { }
    private val garageDoorService = configuration.getBean<GarageDoorService>(GarageDoorService::class)
    private val underFloopHeaterService = configuration.getBean<UnderFloorHeaterService>(UnderFloorHeaterService::class)
    private val subscriptions = mutableMapOf<String, Subscription<*>>()
    private val evChargingStationConnection =
        configuration.getBean<EVChargingStationConnection>(EVChargingStationConnection::class)

    @OnOpen
    fun onWebSocketConnect(sess: Session) {
        logger.info("$instanceId Socket connected: $sess")
    }

    @OnMessage
    fun onWebSocketText(argSession: Session, argMessage: String) {
        val websocketMessage: WebsocketMessage = objectMapper.readValue(argMessage)

        if (websocketMessage.type != WebsocketMessageType.rpcRequest) {
            return
        }

        val rpcRequest = websocketMessage.rpcRequest!!
        val response: RpcResponse = when (rpcRequest.type) {
            getEvCharingStationFirmwareVersion -> RpcResponse(
                evCharingStationFirmwareVersion = evChargingStationConnection.getFirmwareVersion(rpcRequest.evCharingStationId!!)
            )
            garageDoorExtendAutoClose -> {
                garageDoorService.updateAutoCloseAfter(rpcRequest.garageDoorChangeAutoCloseDeltaInSeconds!!)
                RpcResponse(
                    garageStatus = garageDoorService.getCurrentState()
                )
            }
            updateUnderFloorHeaterMode -> {
                val update =
                    underFloopHeaterService.update(rpcRequest.updateUnderFloorHeaterMode!!)
                RpcResponse(
                    updateUnderFloorHeaterModeSuccess = update,
                    underFloorHeaterStatus = underFloopHeaterService.getCurrentState()
                )
            }
            getUnderFloorHeaterStatus -> RpcResponse(underFloorHeaterStatus = underFloopHeaterService.getCurrentState())
            openGarageDoor -> {
                val sendCommand = garageDoorService.sendCommand(true)
                RpcResponse(
                    garageCommandSendSuccess = sendCommand,
                    garageStatus = garageDoorService.getCurrentState()
                )
            }
            closeGarageDoor -> {
                val sendCommand = garageDoorService.sendCommand(false)
                RpcResponse(
                    garageCommandSendSuccess = sendCommand,
                    garageStatus = garageDoorService.getCurrentState()

                )
            }
            getGarageStatus -> RpcResponse(garageStatus = garageDoorService.getCurrentState())
            subscribe -> {
                val subscribe = rpcRequest.subscribe!!
                val subscriptionId = subscribe.subscriptionId

                val existing = subscriptions[subscriptionId]
                if (existing == null) {
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
                    logger.info { "$instanceId New subscription id=$subscriptionId type=${subscribe.type}" }
                } else {
                    logger.info { "$instanceId re-subscription id=$subscriptionId type=${subscribe.type}" }
                }

                RpcResponse(subscriptionCreated = true)
            }
            unsubscribe -> {
                val subscriptionId = rpcRequest.unsubscribe!!.subscriptionId
                subscriptions.remove(subscriptionId)?.close()
                logger.info { "$instanceId Removed subscription id=$subscriptionId" }
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
        logger.info("$instanceId Socket Closed: $reason")
    }

    @OnError
    fun onWebSocketError(cause: Throwable) {
        logger.warn("$instanceId ", cause)
    }

    inner class GarageStatusSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<GarageStatus>(subscriptionId, sess) {
        override fun onEvent(e: GarageStatus) {
            logger.info("$instanceId onEvent GarageStatusSubscription $subscriptionId ")
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
            logger.info("$instanceId onEvent UnderFloorHeaterSubscription $subscriptionId ")
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

