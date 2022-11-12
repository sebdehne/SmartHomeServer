package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.ev_charging.ChargingState
import com.dehnes.smarthome.ev_charging.LoadSharingPriority
import java.time.Instant

enum class EvChargingEventType {
    chargingStationDataAndConfig
}

data class EvChargingEvent(
    val type: EvChargingEventType,
    val chargingStationsDataAndConfig: List<EvChargingStationDataAndConfig>? = null,
)

enum class EvChargingStationRequestType {
    getChargingStationsDataAndConfig,
    uploadFirmwareToClient,

    setMode,
    setLoadSharingPriority,
    setChargeRateLimit
}

data class EvChargingStationRequest(
    val type: EvChargingStationRequestType,
    val clientId: String?,
    val firmwareBased64Encoded: String?,

    val newMode: EvChargingMode?,
    val newLoadSharingPriority: LoadSharingPriority?,
    val chargeRateLimit: Int?,
)

data class EvChargingStationResponse(
    val chargingStationsDataAndConfig: List<EvChargingStationDataAndConfig>,
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
    val powerConnectionId: String,
    val connectedSince: Long = System.currentTimeMillis()
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
    val chargeRateLimit: Int,
)

enum class EvChargingMode {
    ON,
    OFF,
    ChargeDuringCheapHours
}

enum class ProximityPilotAmps(
    val value: Int,
    val ampValue: Int
) {
    Amp13(0, 13),
    Amp20(1, 20),
    Amp32(2, 32),
    NoCable(3, 0);

    fun toAmps() = when (this) {
        NoCable -> 13
        Amp13 -> 13
        Amp20 -> 20
        Amp32 -> 32
    }
}

