package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.zwave.StairsHeatingData

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
    val currentData: StairsHeatingData
)


