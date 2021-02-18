package com.dehnes.smarthome.api.dtos

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


