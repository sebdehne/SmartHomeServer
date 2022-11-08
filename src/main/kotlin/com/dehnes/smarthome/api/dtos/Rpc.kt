package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.victron.ESSValues

enum class RequestType {
    subscribe,
    unsubscribe,

    garageRequest,
    underFloorHeaterRequest,
    evChargingStationRequest,
    environmentSensorRequest,
    videoBrowser,
    quickStats,
    essValues,
    essRequest
}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,

    val garageRequest: GarageRequest?,
    val underFloorHeaterRequest: UnderFloorHeaterRequest?,
    val evChargingStationRequest: EvChargingStationRequest?,
    val environmentSensorRequest: EnvironmentSensorRequest?,
    val videoBrowserRequest: VideoBrowserRequest?,
    val essRequest: EssRequest?,
)

data class RpcResponse(
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,

    val garageResponse: GarageResponse? = null,
    val underFloorHeaterResponse: UnderFloorHeaterResponse? = null,
    val evChargingStationResponse: EvChargingStationResponse? = null,
    val environmentSensorResponse: EnvironmentSensorResponse? = null,
    val videoBrowserResponse: VideoBrowserResponse? = null,
    val quickStatsResponse: QuickStatsResponse? = null,
    val essValues: ESSValues? = null,
)
