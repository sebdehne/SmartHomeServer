package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.config.LEDStripeStatus
import com.dehnes.smarthome.config.LightLedMode

data class GarageLightStatus(
    val ceilingLightIsOn: Boolean,
    val ledStripeStatus: LEDStripeStatus,
    val utcTimestampInMs: Long,
    val ledStripeLowMillivolts: Int,
    val ledStripeCurrentMode: LightLedMode,
)

data class GarageLightRequest(
    val type: GarageLightRequestType,
    val ledStripeLowMillivolts: Int? = null,
    val setLedStripeMode: LightLedMode? = null,
)

data class GarageLightResponse(
    val status: GarageLightStatus? = null,
    val commandSendSuccess: Boolean? = null,
)

enum class GarageLightRequestType {
    switchOnCeilingLight,
    switchOffCeilingLight,

    setLedStripeLowMillivolts,
    setLedStripeMode,

    switchLedStripeOff,
    switchLedStripeOnLow,
    switchLedStripeOnHigh,

    getStatus,
}
