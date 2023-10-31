package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.config.UserSettings
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionData
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionQuery
import com.dehnes.smarthome.victron.ESSState
import com.dehnes.smarthome.victron.ESSWrite
import com.dehnes.smarthome.victron.WriteBms

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
    energyConsumptionQuery,

    essRead,
    essWrite,

    writeBms,

    readEnergyPricingSettings,
    writeEnergyPricingSettings,

    readAllUserSettings,
    writeUserSettings,

    stairsHeatingRequest,
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
    val energyConsumptionQuery: EnergyConsumptionQuery?,
    val writeUserSettings: WriteUserSettings?,
    val writeBms: WriteBms? = null,
    val stairsHeatingRequest: StairsHeatingRequest? = null,
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
    val energyConsumptionData: EnergyConsumptionData? = null,
    val allUserSettings: Map<String, UserSettings>? = null,
    val stairsHeatingResponse: StairsHeatingResponse? = null,
)
