package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.config.UserSettings
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionData
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionQuery
import com.dehnes.smarthome.garage.GarageVentilationState
import com.dehnes.smarthome.garage.HoermannE4Command
import com.dehnes.smarthome.victron.ESSState
import com.dehnes.smarthome.victron.ESSWrite
import com.dehnes.smarthome.victron.WriteBms

enum class RequestType {
    subscribe,
    unsubscribe,

    userSettings,

    garageLightRequest,
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

    dnsBlockingSet,
    dnsBlockingUpdateStandardLists,
    blockedMacsSet,

    sendHoermannE4Command,
    garageVentilationRequest,
}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,

    val garageLightRequest: GarageLightRequest?,
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
    val dnsBlockingLists: List<String>? = null,
    val blockedMacs: List<String>? = null,
    val hoermannE4Command: HoermannE4Command? = null,
    val garageVentilationCommandMilliVolts: Int? = null,
)

data class RpcResponse(
    val errorMsg: String? = null,
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,

    val garageLightResponse: GarageLightResponse? = null,
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
    val hoermannE4CommandResult: Boolean? = null,
    val garageVentilationState: GarageVentilationState? = null,
)
