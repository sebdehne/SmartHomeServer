package com.dehnes.smarthome.api.dtos

data class GarageLightStatus(
    val ceilingLightIsOn: Boolean,
    val ledStripeStatus: LEDStripeStatus,
    val timestampDelta: Long,
    val utcTimestampInMs: Long
)

enum class LEDStripeStatus {
    off,
    onLow,
    onHigh
}

data class GarageLightRequest(
    val type: GarageLightRequestType,
)

data class GarageLightResponse(
    val status: GarageLightStatus? = null,
    val commandSendSuccess: Boolean? = null,
)

enum class GarageLightRequestType {
    switchOnCeilingLight,
    switchOffCeilingLight,
    switchLedStripeOff,
    switchLedStripeOnLow,
    switchLedStripeOnHigh,

    getStatus,
}
