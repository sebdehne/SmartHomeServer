package com.dehnes.smarthome.api.dtos

enum class Mode {
    ON, OFF, MANUAL
}

data class UnderFloorHeaterRequest(
    val type: UnderFloorHeaterRequestType,
    val newMode: UnderFloorHeaterMode?,
    val newTargetTemperature: Int?,
    val newMostExpensiveHoursToSkip: Int?,
    val firmwareBased64Encoded: String?,
)

data class UnderFloorHeaterResponse(
    val underFloorHeaterStatus: UnderFloorHeaterStatus? = null,
    val updateUnderFloorHeaterModeSuccess: Boolean? = null,
    val adjustTimeSuccess: Boolean? = null,
    val firmwareUploadSuccess: Boolean? = null,
    val firmwareUpgradeState: FirmwareUpgradeState? = null
)

enum class UnderFloorHeaterRequestType {
    getStatus,
    updateMode,
    updateTargetTemperature,
    updateMostExpensiveHoursToSkip,
    adjustTime,
    firmwareUpgrade
}

data class UnderFloorHeaterStatus(
    val mode: UnderFloorHeaterMode,
    val status: OnOff,
    val targetTemperature: Int,
    val mostExpensiveHoursToSkip: Int,
    val waitUntilCheapHour: Long?,
    val timestampDelta: Long,
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
