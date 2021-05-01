package com.dehnes.smarthome.api.dtos

enum class Mode {
    ON, OFF, MANUAL
}

data class UnderFloorHeaterRequest(
    val type: UnderFloorHeaterRequestType,
    val newMode: UnderFloorHeaterMode?,
    val newTargetTemperature: Int?,
    val newMostExpensiveHoursToSkip: Int?
)

data class UnderFloorHeaterResponse(
    val underFloorHeaterStatus: UnderFloorHeaterStatus?,
    val updateUnderFloorHeaterModeSuccess: Boolean? = null
)

enum class UnderFloorHeaterRequestType {
    getStatus,
    updateMode,
    updateTargetTemperature,
    updateMostExpensiveHoursToSkip
}

data class UnderFloorHeaterStatus(
    val mode: UnderFloorHeaterMode,
    val status: OnOff,
    val targetTemperature: Int,
    val mostExpensiveHoursToSkip: Int,
    val waitUntilCheapHour: Long?,
    val fromController: UnderFloorHeaterStatusFromController?
)

data class UnderFloorHeaterStatusFromController(
    val receivedAt: Long,
    val currentTemperature: Int
)

enum class UnderFloorHeaterMode(val mode: Mode) {
    permanentOn(Mode.ON),
    permanentOff(Mode.OFF),
    constantTemperature(Mode.MANUAL)
}

enum class OnOff {
    on,
    off
}
