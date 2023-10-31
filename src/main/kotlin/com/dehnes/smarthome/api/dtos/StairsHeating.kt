package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.zwave.StairsHeatingData
import com.dehnes.smarthome.zwave.StairsHeatingSettings

enum class StairsHeatingType {
    enableDisable,
    get,
    increaseTargetTemp,
    decreaseTargetTemp,
    increaseOutsideLowerTemp,
    decreaseOutsideLowerTemp,
    increaseOutsideUpperTemp,
    decreaseOutsideUpperTemp,
}

data class StairsHeatingRequest(
    val type: StairsHeatingType,
)

data class StairsHeatingResponse(
    val data: StairsHeatingData?,
    val settings: StairsHeatingSettings,
)

