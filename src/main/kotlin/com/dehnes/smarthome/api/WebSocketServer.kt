package com.dehnes.smarthome.api

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.api.dtos.RequestType.*
import com.dehnes.smarthome.configuration
import com.dehnes.smarthome.external.EVChargingStationConnection
import com.dehnes.smarthome.service.GarageDoorService
import com.dehnes.smarthome.service.UnderFloorHeaterService
import com.dehnes.smarthome.service.ev_charging_station.FirmwareUploadService
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
    private val firmwareUploadService =
        configuration.getBean<FirmwareUploadService>(FirmwareUploadService::class)

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
            subscribe -> {
                val subscribe = rpcRequest.subscribe!!
                val subscriptionId = subscribe.subscriptionId

                val existing = subscriptions[subscriptionId]
                if (existing == null) {
                    val sub = when (subscribe.type) {
                        SubscriptionType.getGarageStatus -> GarageStatusSubscription(subscriptionId, argSession).apply {
                            garageDoorService.listeners[subscriptionId] = this::onEvent
                        }
                        SubscriptionType.getUnderFloorHeaterStatus -> UnderFloorHeaterSubscription(
                            subscriptionId, argSession
                        ).apply {
                            underFloopHeaterService.listeners[subscriptionId] = this::onEvent
                        }
                        SubscriptionType.evChargingStationConnections -> EvChargingStationSubscription(
                            subscriptionId,
                            argSession
                        ).apply {
                            evChargingStationConnection.listeners[subscriptionId] = this::onEvent
                        }
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
            garageRequest -> RpcResponse(garageResponse = garageRequest(rpcRequest.garageRequest!!))
            underFloorHeaterRequest -> RpcResponse(underFloorHeaterResponse = underFloorHeaterRequest(rpcRequest.underFloorHeaterRequest!!))
            evChargingStationRequest -> RpcResponse(evChargingStationResponse = evChargingStationRequest(rpcRequest.evChargingStationRequest!!))
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

    private fun evChargingStationRequest(request: EvChargingStationRequest) = when (request.type) {
        EvChargingStationRequestType.getConnectedClients -> EvChargingStationResponse(
            connectedClients = evChargingStationConnection.getConnectedClients()
        )
        EvChargingStationRequestType.uploadFirmwareToClient -> EvChargingStationResponse(
            uploadFirmwareToClientResult = firmwareUploadService.uploadVersion(
                request.clientId!!,
                request.firmwareBased64Encoded!!
            )
        )
    }

    private fun underFloorHeaterRequest(request: UnderFloorHeaterRequest) = when (request.type) {
        UnderFloorHeaterRequestType.updateUnderFloorHeaterMode -> {
            val update =
                underFloopHeaterService.update(request.updateUnderFloorHeaterMode!!)
            UnderFloorHeaterResponse(
                updateUnderFloorHeaterModeSuccess = update,
                underFloorHeaterStatus = underFloopHeaterService.getCurrentState()
            )
        }
        UnderFloorHeaterRequestType.getUnderFloorHeaterStatus -> UnderFloorHeaterResponse(
            underFloorHeaterStatus = underFloopHeaterService.getCurrentState()
        )
    }

    private fun garageRequest(request: GarageRequest) = when (request.type) {
        GarageRequestType.garageDoorExtendAutoClose -> {
            garageDoorService.updateAutoCloseAfter(request.garageDoorChangeAutoCloseDeltaInSeconds!!)
            GarageResponse(
                garageStatus = garageDoorService.getCurrentState()
            )
        }
        GarageRequestType.openGarageDoor -> {
            val sendCommand = garageDoorService.sendCommand(true)
            GarageResponse(
                garageCommandSendSuccess = sendCommand,
                garageStatus = garageDoorService.getCurrentState()
            )
        }
        GarageRequestType.closeGarageDoor -> {
            val sendCommand = garageDoorService.sendCommand(false)
            GarageResponse(
                garageCommandSendSuccess = sendCommand,
                garageStatus = garageDoorService.getCurrentState()
            )
        }
        GarageRequestType.getGarageStatus -> GarageResponse(garageStatus = garageDoorService.getCurrentState())
    }

    @OnClose
    fun onWebSocketClose(reason: CloseReason) {
        subscriptions.values.toList().forEach { it.close() }
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
                        notify = Notify(subscriptionId, e, null, null)
                    )
                )
            )
        }

        override fun close() {
            garageDoorService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }

    inner class EvChargingStationSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<Event>(subscriptionId, sess) {
        override fun onEvent(e: Event) {
            logger.info("$instanceId onEvent EvChargingStationSubscription $subscriptionId ")
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(subscriptionId, null, null, e)
                    )
                )
            )
        }

        override fun close() {
            evChargingStationConnection.listeners.remove(subscriptionId)
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
                        notify = Notify(subscriptionId, null, e, null)
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

