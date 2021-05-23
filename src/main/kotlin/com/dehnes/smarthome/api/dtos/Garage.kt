package com.dehnes.smarthome.api.dtos

data class GarageStatus(
    val lightIsOn: Boolean,
    val doorStatus: DoorStatus,
    val autoCloseAfter: Long?,
    val timestampDelta: Long,
    val firmwareVersion: Int,
    val utcTimestampInMs: Long
)

enum class DoorStatus(
    val influxDbValue: Int
) {
    doorClosed(0),
    doorOpen(1),
    doorClosing(2),
    doorOpening(3),
    doorMiddle(4);

    companion object {
        fun parse(ch1: Boolean, ch2: Boolean) = when {
            !ch1 -> doorOpen
            !ch2 -> doorClosed
            else -> doorMiddle
        }
    }
}

data class GarageRequest(
    val type: GarageRequestType,
    val garageDoorChangeAutoCloseDeltaInSeconds: Long?,
    val firmwareBased64Encoded: String?,
)

data class GarageResponse(
    val garageStatus: GarageStatus? = null,
    val garageCommandSendSuccess: Boolean? = null,
    val garageCommandAdjustTimeSuccess: Boolean? = null,
    val firmwareUploadSuccess: Boolean? = null,
    val firmwareUpgradeState: FirmwareUpgradeState? = null
)

enum class GarageRequestType {
    openGarageDoor,
    closeGarageDoor,

    getGarageStatus,
    garageDoorExtendAutoClose,
    adjustTime,
    firmwareUpgrade
}
