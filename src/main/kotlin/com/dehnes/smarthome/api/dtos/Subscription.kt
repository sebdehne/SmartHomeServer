package com.dehnes.smarthome.api.dtos

enum class SubscriptionType {
    getGarageStatus,
    getUnderFloorHeaterStatus,
    evChargingStationEvents,
    environmentSensorEvents
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
    val underFloorHeaterStatus: UnderFloorHeaterStatus?,
    val evChargingStationEvent: EvChargingEvent?,
    val environmentSensorEvent: EnvironmentSensorEvent?
)
