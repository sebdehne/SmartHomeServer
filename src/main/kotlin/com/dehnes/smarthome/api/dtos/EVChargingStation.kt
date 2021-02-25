package com.dehnes.smarthome.api.dtos

enum class EvChargingStationRequestType{
    getConnectedClients,
    uploadFirmwareToClient
}

data class EvChargingStationRequest(
    val type: EvChargingStationRequestType,
    val clientId: String?,
    val firmwareBased64Encoded: String?,
)

data class EvChargingStationResponse(
    val connectedClients: List<EvChargingStationClient>? = null,
    val uploadFirmwareToClientResult: Boolean? = null
)

data class EvChargingStationClient(
    val clientId: String,
    val addr: String,
    val port: Int,
    val firmwareVersion: Int
)

enum class EventType {
    clientConnectionsChanged
}

data class Event(
    val eventType: EventType,
    val connectedClients: List<EvChargingStationClient>? = null
)
