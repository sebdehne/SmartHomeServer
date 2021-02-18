package com.dehnes.smarthome.api.dtos

import java.time.Instant

data class GarageStatus(
    val lightIsOn: Boolean,
    val doorStatus: DoorStatus,
    val autoCloseAfter: Long?,
    val utcTimestampInMs: Long = Instant.now().toEpochMilli()
)

enum class DoorStatus(
    val influxDbValue: Int
) {
    doorClosed(0),
    doorOpen(1),
    doorClosing(2),
    doorOpening(3)
}

data class GarageRequest(
    val type: GarageRequestType,
    val garageDoorChangeAutoCloseDeltaInSeconds: Long?
)

data class GarageResponse(
    val garageStatus: GarageStatus?,
    val garageCommandSendSuccess: Boolean? = null
)

enum class GarageRequestType {
    openGarageDoor,
    closeGarageDoor,

    getGarageStatus,
    garageDoorExtendAutoClose,
}
