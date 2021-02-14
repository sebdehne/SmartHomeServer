package com.dehnes.smarthome.api

import com.dehnes.smarthome.service.Mode
import java.time.Instant

data class WebsocketMessage(
    val id: String,
    val type: WebsocketMessageType,
    val rpcRequest: RpcRequest? = null,
    val rpcResponse: RpcResponse? = null,
    val notify: Notify? = null
)

enum class WebsocketMessageType {
    rpcRequest,
    rpcResponse,
    notify
}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,
    val updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode?
)

data class Subscribe(
    val subscriptionId: String,
    val type: RequestType,
)

data class Unsubscribe(
    val subscriptionId: String
)

data class RpcResponse(
    val garageStatus: GarageStatus? = null,
    val underFloorHeaterStatus: UnderFloorHeaterStatus? = null,
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,
    val garageCommandSendSuccess: Boolean? = null,
    val updateUnderFloorHeaterModeSuccess: Boolean? = null,
)

data class Notify(
    val subscriptionId: String,
    val garageStatus: GarageStatus?,
    val underFloorHeaterStatus: UnderFloorHeaterStatus?
)

enum class RequestType {
    getGarageStatus,
    openGarageDoor,
    closeGarageDoor,

    getUnderFloorHeaterStatus,
    updateUnderFloorHeaterMode,

    subscribe,
    unsubscribe
}

data class GarageStatus(
    val lightIsOn: Boolean,
    val doorIsOpen: Boolean,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

enum class UnderFloorHeaterMode(
    val mode: Mode
) {
    permanentOn(Mode.ON),
    permanentOff(Mode.OFF),
    constantTemperature(Mode.MANUAL)
}

enum class OnOff {
    on,
    off
}

data class UnderFloorHeaterConstantTemperaturStatus(
    val targetTemperatur: Int
)

data class UnderFloorHeaterStatus(
    val mode: UnderFloorHeaterMode,
    val status: OnOff,
    val currentTemperatur: Int,
    val constantTemperaturStatus: UnderFloorHeaterConstantTemperaturStatus,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

data class UpdateUnderFloorHeaterMode(
    val newMode: UnderFloorHeaterMode,
    val newTargetTemperatur: Int
)

