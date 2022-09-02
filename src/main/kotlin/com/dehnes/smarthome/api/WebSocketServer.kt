package com.dehnes.smarthome.api

import com.dehnes.smarthome.VideoBrowser
import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.api.dtos.RequestType.*
import com.dehnes.smarthome.configuration
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.environment_sensors.EnvironmentSensorService
import com.dehnes.smarthome.ev_charging.EvChargingService
import com.dehnes.smarthome.ev_charging.FirmwareUploadService
import com.dehnes.smarthome.garage_door.GarageController
import com.dehnes.smarthome.heating.UnderFloorHeaterService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.websocket.CloseReason
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
import jakarta.websocket.Session
import mu.KotlinLogging
import java.io.Closeable
import java.util.*

// one instance per sessions
class WebSocketServer : Endpoint() {

    private val instanceId = UUID.randomUUID().toString()

    private val objectMapper = configuration.getBean<ObjectMapper>(ObjectMapper::class)
    private val logger = KotlinLogging.logger { }
    private val garageDoorService = configuration.getBean<GarageController>(GarageController::class)
    private val underFloopHeaterService = configuration.getBean<UnderFloorHeaterService>(UnderFloorHeaterService::class)
    private val subscriptions = mutableMapOf<String, Subscription<*>>()
    private val evChargingService =
        configuration.getBean<EvChargingService>(EvChargingService::class)
    private val firmwareUploadService =
        configuration.getBean<FirmwareUploadService>(FirmwareUploadService::class)
    private val loRaSensorBoardService =
        configuration.getBean<EnvironmentSensorService>(EnvironmentSensorService::class)
    private val videoBrowser =
        configuration.getBean<VideoBrowser>(VideoBrowser::class)
    private val quickStatsService =
        configuration.getBean<QuickStatsService>(QuickStatsService::class)

    override fun onOpen(sess: Session, p1: EndpointConfig?) {
        logger.info("$instanceId Socket connected: $sess")
        sess.addMessageHandler(String::class.java) { msg -> onWebSocketText(sess, msg) }
    }

    override fun onClose(session: Session, closeReason: CloseReason) {
        subscriptions.values.toList().forEach { it.close() }
        logger.info("$instanceId Socket Closed: $closeReason")
    }

    override fun onError(session: Session?, cause: Throwable?) {
        logger.warn("$instanceId ", cause)
    }

    fun onWebSocketText(argSession: Session, argMessage: String) {
        val websocketMessage: WebsocketMessage = objectMapper.readValue(argMessage)

        if (websocketMessage.type != WebsocketMessageType.rpcRequest) {
            return
        }

        val rpcRequest = websocketMessage.rpcRequest!!
        val response: RpcResponse = when (rpcRequest.type) {
            quickStats -> RpcResponse(quickStatsResponse = quickStatsService.getStats())
            subscribe -> {
                val subscribe = rpcRequest.subscribe!!
                val subscriptionId = subscribe.subscriptionId

                val existing = subscriptions[subscriptionId]
                if (existing == null) {
                    val sub = when (subscribe.type) {
                        SubscriptionType.quickStatsEvents  -> QuickStatsSubscription(subscriptionId, argSession).apply {
                            quickStatsService.listeners[subscriptionId] = this::onEvent
                        }
                        SubscriptionType.getGarageStatus -> GarageStatusSubscription(subscriptionId, argSession).apply {
                            garageDoorService.listeners[subscriptionId] = this::onEvent
                        }

                        SubscriptionType.getUnderFloorHeaterStatus -> UnderFloorHeaterSubscription(
                            subscriptionId, argSession
                        ).apply {
                            underFloopHeaterService.listeners[subscriptionId] = this::onEvent
                        }

                        SubscriptionType.evChargingStationEvents -> EvChargingStationSubscription(
                            subscriptionId,
                            argSession
                        ).apply {
                            evChargingService.listeners[subscriptionId] = this::onEvent
                        }

                        SubscriptionType.environmentSensorEvents -> EnvironmentSensorSubscription(
                            subscriptionId,
                            argSession
                        ).apply {
                            loRaSensorBoardService.listeners[subscriptionId] = this::onEvent
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
            environmentSensorRequest -> RpcResponse(environmentSensorResponse = environmentSensorRequest(rpcRequest.environmentSensorRequest!!))
            RequestType.videoBrowser -> RpcResponse(videoBrowserResponse = videoBrowser.rpc(rpcRequest.videoBrowserRequest!!))
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

    private fun environmentSensorRequest(request: EnvironmentSensorRequest) = when (request.type) {
        EnvironmentSensorRequestType.getAllEnvironmentSensorData -> loRaSensorBoardService.getEnvironmentSensorResponse()
        EnvironmentSensorRequestType.scheduleFirmwareUpgrade -> {
            loRaSensorBoardService.firmwareUpgrade(request.sensorId!!, true)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.cancelFirmwareUpgrade -> {
            loRaSensorBoardService.firmwareUpgrade(request.sensorId!!, false)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.scheduleTimeAdjustment -> {
            loRaSensorBoardService.timeAdjustment(request.sensorId, true)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.cancelTimeAdjustment -> {
            loRaSensorBoardService.timeAdjustment(request.sensorId, false)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.scheduleReset -> {
            loRaSensorBoardService.configureReset(request.sensorId, true)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.cancelReset -> {
            loRaSensorBoardService.configureReset(request.sensorId, false)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.adjustSleepTimeInSeconds -> {
            loRaSensorBoardService.adjustSleepTimeInSeconds(request.sensorId!!, request.sleepTimeInSecondsDelta!!)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }

        EnvironmentSensorRequestType.uploadFirmware -> {
            loRaSensorBoardService.setFirmware(request.firmwareFilename!!, request.firmwareBased64Encoded!!)
            loRaSensorBoardService.getEnvironmentSensorResponse()
        }
    }

    private fun evChargingStationRequest(request: EvChargingStationRequest) = when (request.type) {
        EvChargingStationRequestType.uploadFirmwareToClient -> EvChargingStationResponse(
            uploadFirmwareToClientResult = firmwareUploadService.uploadVersion(
                request.clientId!!,
                request.firmwareBased64Encoded!!
            ),
            chargingStationsDataAndConfig = evChargingService.getChargingStationsDataAndConfig()
        )

        EvChargingStationRequestType.getChargingStationsDataAndConfig -> EvChargingStationResponse(
            chargingStationsDataAndConfig = evChargingService.getChargingStationsDataAndConfig()
        )

        EvChargingStationRequestType.setLoadSharingPriority -> EvChargingStationResponse(
            configUpdated = evChargingService.setPriorityFor(request.clientId!!, request.newLoadSharingPriority!!),
            chargingStationsDataAndConfig = evChargingService.getChargingStationsDataAndConfig()
        )

        EvChargingStationRequestType.setMode -> EvChargingStationResponse(
            configUpdated = evChargingService.updateMode(request.clientId!!, request.newMode!!),
            chargingStationsDataAndConfig = evChargingService.getChargingStationsDataAndConfig()
        )

        EvChargingStationRequestType.setSkipPercentExpensiveHours -> EvChargingStationResponse(
            configUpdated = evChargingService.setSkipPercentExpensiveHours(
                request.clientId!!,
                request.skipPercentExpensiveHours!!
            ),
            chargingStationsDataAndConfig = evChargingService.getChargingStationsDataAndConfig()
        )

        EvChargingStationRequestType.setChargeRateLimit -> EvChargingStationResponse(
            configUpdated = evChargingService.setChargeRateLimitFor(
                request.clientId!!,
                request.chargeRateLimit!!
            ),
            chargingStationsDataAndConfig = evChargingService.getChargingStationsDataAndConfig()
        )
    }

    private fun underFloorHeaterRequest(request: UnderFloorHeaterRequest) = when (request.type) {
        UnderFloorHeaterRequestType.updateMode -> {
            val success = underFloopHeaterService.updateMode(request.newMode!!)
            underFloopHeaterService.getCurrentState().copy(
                updateUnderFloorHeaterModeSuccess = success
            )
        }

        UnderFloorHeaterRequestType.setSkipPercentExpensiveHours -> {
            val success = underFloopHeaterService.setEkipPercentExpensiveHours(request.skipPercentExpensiveHours!!)
            underFloopHeaterService.getCurrentState().copy(
                updateUnderFloorHeaterModeSuccess = success
            )
        }

        UnderFloorHeaterRequestType.updateTargetTemperature -> {
            val success = underFloopHeaterService.updateTargetTemperature(request.newTargetTemperature!!)
            underFloopHeaterService.getCurrentState().copy(
                updateUnderFloorHeaterModeSuccess = success
            )
        }

        UnderFloorHeaterRequestType.getStatus -> underFloopHeaterService.getCurrentState()
        UnderFloorHeaterRequestType.adjustTime -> {
            val success = underFloopHeaterService.adjustTime()
            underFloopHeaterService.getCurrentState().copy(
                adjustTimeSuccess = success
            )
        }

        UnderFloorHeaterRequestType.firmwareUpgrade -> {
            val success = underFloopHeaterService.startFirmwareUpgrade(request.firmwareBased64Encoded!!)
            underFloopHeaterService.getCurrentState().copy(
                firmwareUploadSuccess = success
            )
        }
    }

    private fun garageRequest(request: GarageRequest) = when (request.type) {
        GarageRequestType.garageDoorExtendAutoClose -> {
            garageDoorService.updateAutoCloseAfter(request.garageDoorChangeAutoCloseDeltaInSeconds!!)
            garageDoorService.getCurrentState()
        }

        GarageRequestType.openGarageDoor -> {
            val sendCommand = garageDoorService.sendCommand(true)
            garageDoorService.getCurrentState().copy(
                garageCommandSendSuccess = sendCommand
            )
        }

        GarageRequestType.closeGarageDoor -> {
            val sendCommand = garageDoorService.sendCommand(false)
            garageDoorService.getCurrentState().copy(
                garageCommandSendSuccess = sendCommand,
            )
        }

        GarageRequestType.getGarageStatus -> garageDoorService.getCurrentState()
        GarageRequestType.adjustTime -> {
            garageDoorService.getCurrentState().copy(
                garageCommandAdjustTimeSuccess = garageDoorService.adjustTime()
            )
        }

        GarageRequestType.firmwareUpgrade -> {
            garageDoorService.getCurrentState().copy(
                firmwareUploadSuccess = garageDoorService.startFirmwareUpgrade(request.firmwareBased64Encoded!!),
            )
        }
    }

    inner class GarageStatusSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<GarageResponse>(subscriptionId, sess) {
        override fun onEvent(e: GarageResponse) {
            logger.info("$instanceId onEvent GarageStatusSubscription $subscriptionId ")
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(
                            subscriptionId,
                            e,
                            null,
                            null,
                            null,
                            null,
                        )
                    )
                )
            )
        }

        override fun close() {
            garageDoorService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }

    inner class QuickStatsSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<QuickStatsResponse>(subscriptionId, sess) {
        override fun onEvent(e: QuickStatsResponse) {
            logger.info("$instanceId onEvent GarageStatusSubscription $subscriptionId ")
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(
                            subscriptionId,
                            null,
                            null,
                            null,
                            null,
                            e
                        )
                    )
                )
            )
        }

        override fun close() {
            garageDoorService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }

    inner class EnvironmentSensorSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<EnvironmentSensorEvent>(subscriptionId, sess) {
        override fun onEvent(e: EnvironmentSensorEvent) {
            logger.info("$instanceId onEvent EnvironmentSensorSubscription $subscriptionId ")
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(
                            subscriptionId,
                            null,
                            null,
                            null,
                            e,
                            null,
                        )
                    )
                )
            )
        }

        override fun close() {
            loRaSensorBoardService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }

    inner class EvChargingStationSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<EvChargingEvent>(subscriptionId, sess) {
        override fun onEvent(e: EvChargingEvent) {
            logger.info("$instanceId onEvent EvChargingStationSubscription $subscriptionId ")
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(
                            subscriptionId,
                            null,
                            null,
                            e,
                            null,
                            null,
                        )
                    )
                )
            )
        }

        override fun close() {
            evChargingService.listeners.remove(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }

    inner class UnderFloorHeaterSubscription(
        subscriptionId: String,
        sess: Session
    ) : Subscription<UnderFloorHeaterResponse>(subscriptionId, sess) {
        override fun onEvent(e: UnderFloorHeaterResponse) {
            logger.info("$instanceId onEvent UnderFloorHeaterSubscription $subscriptionId ")
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = Notify(
                            subscriptionId,
                            null,
                            e,
                            null,
                            null,
                            null,
                        )
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

