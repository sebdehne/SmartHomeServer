package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.users.UserSettings
import com.dehnes.smarthome.victron.ESSState
import com.dehnes.smarthome.victron.ESSWrite

enum class RequestType {
    subscribe,
    unsubscribe,

    userSettings,

    garageRequest,
    underFloorHeaterRequest,
    evChargingStationRequest,
    environmentSensorRequest,
    videoBrowser,
    quickStats,

    essRead,
    essWrite,

    readEnergyPricingSettings,
    writeEnergyPricingSettings,
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
    val essWrite: ESSWrite?,
    val energyPricingSettingsWrite: EnergyPricingSettingsWrite?,
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
    val essState: ESSState? = null,
    val energyPricingSettingsRead: EnergyPricingSettingsRead? = null,
    val userSettings: UserSettings? = null,
)
