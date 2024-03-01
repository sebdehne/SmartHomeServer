package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.victron.ESSState
import com.dehnes.smarthome.victron.ESSValues

enum class SubscriptionType {
    getGarageStatus,
    getUnderFloorHeaterStatus,
    evChargingStationEvents,
    environmentSensorEvents,
    quickStatsEvents,
    essState,
    dnsBlockingGet
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
    val garageStatus: GarageResponse? = null,
    val underFloorHeaterStatus: UnderFloorHeaterResponse? = null,
    val evChargingStationEvent: EvChargingEvent? = null,
    val environmentSensorEvent: EnvironmentSensorEvent? = null,
    val quickStatsResponse: QuickStatsResponse? = null,
    val essState: ESSState? = null,
    val dnsBlockingState: DnsBlockingState? = null,
)
