package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.firewall_router.FirewallState
import com.dehnes.smarthome.garage.GarageVentilationState
import com.dehnes.smarthome.garage.HoermannE4Broadcast
import com.dehnes.smarthome.victron.ESSState

enum class SubscriptionType {
    getGarageLightStatus,
    getGarageDoorStatus,
    getGarageVentilationStatus,
    getUnderFloorHeaterStatus,
    evChargingStationEvents,
    environmentSensorEvents,
    quickStatsEvents,
    essState,
    firewall
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
    val garageStatus: GarageLightStatus? = null,
    val underFloorHeaterStatus: UnderFloorHeaterResponse? = null,
    val evChargingStationEvent: EvChargingEvent? = null,
    val environmentSensorEvent: EnvironmentSensorEvent? = null,
    val quickStatsResponse: QuickStatsResponse? = null,
    val essState: ESSState? = null,
    val firewallState: FirewallState? = null,
    val hoermannE4Broadcast: HoermannE4Broadcast? = null,
    val garageVentilationState: GarageVentilationState? = null,
)
