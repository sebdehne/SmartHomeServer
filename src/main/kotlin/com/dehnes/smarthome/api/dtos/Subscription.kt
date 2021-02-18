package com.dehnes.smarthome.api.dtos

enum class SubscriptionType {
    getGarageStatus,
    getUnderFloorHeaterStatus,
    evChargingStationConnections
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
    val garageStatus: GarageStatus?,
    val underFloorHeaterStatus: UnderFloorHeaterStatus?,
    val evChargingStationEvent: Event?
)