package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.ev_charging.ChargingState
import com.dehnes.smarthome.ev_charging.LoadSharingPriority
import java.time.Instant

enum class EvChargingEventType {
    chargingStationDataAndConfig
}

data class EvChargingEvent(
    val type: EvChargingEventType,
    val chargingStationsDataAndConfig: EvChargingStationDataAndConfig? = null,
)

enum class EvChargingStationRequestType {
    getChargingStationsDataAndConfig,
    uploadFirmwareToClient,

    setMode,
    setLoadSharingPriority,
    setNumberOfHoursRequiredFor
}

data class EvChargingStationRequest(
    val type: EvChargingStationRequestType,
    val clientId: String?,
    val firmwareBased64Encoded: String?,

    val newMode: EvChargingMode?,
    val newLoadSharingPriority: LoadSharingPriority?,
    val newNumberOfHoursRequiredFor: Int?
)

data class EvChargingStationResponse(
    val chargingStationsDataAndConfig: List<EvChargingStationDataAndConfig>? = null,
    val uploadFirmwareToClientResult: Boolean? = null,
    val configUpdated: Boolean? = null
)

data class EvChargingStationDataAndConfig(
    val data: EvChargingStationData,
    val config: EVChargingStationConfig,
    val clientConnection: EvChargingStationClient
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
    val reasonChargingUnavailable: String?,
    val chargingStateChangedAt: Long,
    val proximityPilotAmps: ProximityPilotAmps,
    val maxChargingRate: Int,
    val phase1Millivolts: Int,
    val phase2Millivolts: Int,
    val phase3Millivolts: Int,
    val phase1Milliamps: Int,
    val phase2Milliamps: Int,
    val phase3Milliamps: Int,
    val systemUptime: Int,
    val wifiRSSI: Int,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

data class EVChargingStationConfig(
    val mode: EvChargingMode,
    val loadSharingPriority: LoadSharingPriority,
    val numberOfHoursRequiredFor: Int
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
    Amp32(2);

    fun toAmps() = when (this) {
        Amp13 -> 13
        Amp20 -> 20
        Amp32 -> 32
    }
}

