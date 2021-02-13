package com.dehnes.smarthome.api

data class ApiRequest(
    val type: RequestType,
    val subscriptionId: String?
)

data class ApiResponse(
    val value: Any?
)

data class Notify(
    val subscriptionId: String,
    val value: Any?
)

enum class RequestType {
    getGarageStatus,
    subscribeGarageStatus,
    unsubscribeGarageStatus
}

data class GarageStatus(
    val lightIsOn: Boolean,
    val doorIsOpen: Boolean
)

