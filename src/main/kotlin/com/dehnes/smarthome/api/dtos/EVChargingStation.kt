package com.dehnes.smarthome.api.dtos

enum class EvChargingStationRequestType{
    getConnectedClients,
    listAllFirmwareVersions,
    uploadFirmwareToClient
}

data class EvChargingStationRequest(
    val type: EvChargingStationRequestType,
    val clientId: Int?,
    val firmwareVersion: String?
)

data class EvChargingStationResponse(
    val connectedClients: List<EvChargingStationClient>? = null,
    val allFirmwareVersions: List<String>? = null,
    val uploadFirmwareToClientResult: Boolean? = null
)

data class EvChargingStationClient(
    val clientId: Int,
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