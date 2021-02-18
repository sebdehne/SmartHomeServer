package com.dehnes.smarthome.api.dtos

import java.time.Instant

enum class Mode {
    ON, OFF, MANUAL
}

data class UnderFloorHeaterRequest(
    val type: UnderFloorHeaterRequestType,
    val updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode?
)

data class UnderFloorHeaterResponse(
    val underFloorHeaterStatus: UnderFloorHeaterStatus? = null,
    val updateUnderFloorHeaterModeSuccess: Boolean? = null
)

enum class UnderFloorHeaterRequestType {
    updateUnderFloorHeaterMode,
    getUnderFloorHeaterStatus
}

data class UnderFloorHeaterStatus(
    val mode: UnderFloorHeaterMode,
    val status: OnOff,
    val currentTemperature: Int,
    val constantTemperatureStatus: UnderFloorHeaterConstantTemperaturStatus,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

data class UpdateUnderFloorHeaterMode(
    val newMode: UnderFloorHeaterMode,
    val newTargetTemperature: Int?,
    val newMostExpensiveHoursToSkip: Int?
)

enum class UnderFloorHeaterMode(val mode: Mode) {
    permanentOn(Mode.ON),
    permanentOff(Mode.OFF),
    constantTemperature(Mode.MANUAL)
}

data class UnderFloorHeaterConstantTemperaturStatus(
    val targetTemperature: Int,
    val mostExpensiveHoursToSkip: Int,
    val energyPriceCurrentlyTooExpensive: Boolean,
)

enum class OnOff {
    on,
    off
}
