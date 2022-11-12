package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.victron.ESSState
import com.dehnes.smarthome.victron.ESSValues

enum class SubscriptionType {
    getGarageStatus,
    getUnderFloorHeaterStatus,
    evChargingStationEvents,
    environmentSensorEvents,
    quickStatsEvents,
    essState
}

data class Subscribe(
    val subscriptionId: String,
    val type: SubscriptionType,
)

data class Unsubscribe(
    val subscriptionId: String
)

data class Notify(
    val subscriptionId: String,
    val garageStatus: GarageResponse?,
    val underFloorHeaterStatus: UnderFloorHeaterResponse?,
    val evChargingStationEvent: EvChargingEvent?,
    val environmentSensorEvent: EnvironmentSensorEvent?,
    val quickStatsResponse: QuickStatsResponse?,
    val essState: ESSState?,
)
