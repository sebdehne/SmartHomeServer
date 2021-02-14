package com.dehnes.smarthome.api

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
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,
    val garageCommandSendSuccess: Boolean? = null
)

data class Notify(
    val subscriptionId: String,
    val garageStatus: GarageStatus?
)

enum class RequestType {
    getGarageStatus,
    openGarageDoor,
    closeGarageDoor,

    subscribe,
    unsubscribe
}

data class GarageStatus(
    val lightIsOn: Boolean,
    val doorIsOpen: Boolean,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

