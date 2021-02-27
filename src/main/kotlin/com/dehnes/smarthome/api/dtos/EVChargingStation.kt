package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.service.ev_charging_station.ChargingState
import java.time.Instant

enum class EvChargingEventType {
    newConnection,
    closedConnection,
    data
}

data class EvChargingEvent(
    val type: EvChargingEventType,
    val evChargingStationClient: EvChargingStationClient,
    val evChargingStationData: EvChargingStationData?
)

enum class EvChargingStationRequestType {
    getConnectedClients,
    uploadFirmwareToClient,
    getData
}

data class EvChargingStationRequest(
    val type: EvChargingStationRequestType,
    val clientId: String?,
    val firmwareBased64Encoded: String?,
)

data class EvChargingStationResponse(
    val connectedClients: List<EvChargingStationClient>? = null,
    val uploadFirmwareToClientResult: Boolean? = null,
    val evChargingStationData: EvChargingStationData? = null
)

data class EvChargingStationClient(
    val clientId: String,
    val displayName: String,
    val addr: String,
    val port: Int,
    val firmwareVersion: Int,
    val powerConnectionId: String
)

data class EvChargingStationData(
    val chargingState: ChargingState,
    val chargingStateChangedAt: Long,
    val proximityPilotAmps: ProximityPilotAmps,
    val chargingRate: Int,
    val phase1Millivolts: Int,
    val phase2Millivolts: Int,
    val phase3Millivolts: Int,
    val phase1Milliamps: Int,
    val phase2Milliamps: Int,
    val phase3Milliamps: Int,
    val systemUptime: Int,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

enum class EvChargingMode {
    ON,
    OFF,
    ChargeDuringCheapHours
}

enum class ProximityPilotAmps(
    val value: Int
) {
    Amp13(0),
    Amp20(1),
    Amp32(2),
}

