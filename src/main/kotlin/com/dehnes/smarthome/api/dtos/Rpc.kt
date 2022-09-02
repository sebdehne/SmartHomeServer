package com.dehnes.smarthome.api.dtos

enum class RequestType {
    subscribe,
    unsubscribe,

    garageRequest,
    underFloorHeaterRequest,
    evChargingStationRequest,
    environmentSensorRequest,
    videoBrowser,
    quickStats
}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,

    val garageRequest: GarageRequest?,
    val underFloorHeaterRequest: UnderFloorHeaterRequest?,
    val evChargingStationRequest: EvChargingStationRequest?,
    val environmentSensorRequest: EnvironmentSensorRequest?,
    val videoBrowserRequest: VideoBrowserRequest?
)

data class RpcResponse(
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,

    val garageResponse: GarageResponse? = null,
    val underFloorHeaterResponse: UnderFloorHeaterResponse? = null,
    val evChargingStationResponse: EvChargingStationResponse? = null,
    val environmentSensorResponse: EnvironmentSensorResponse? = null,
    val videoBrowserResponse: VideoBrowserResponse? = null,
    val quickStatsResponse: QuickStatsResponse? = null
)
