package com.dehnes.smarthome.api.dtos

enum class EnvironmentSensorRequestType {
    getAllEnvironmentSensorData,
    uploadFirmware,

    scheduleTimeAdjustment,
    cancelTimeAdjustment,
    scheduleFirmwareUpgrade,
    cancelFirmwareUpgrade,
    adjustSleepTimeInSeconds
}

data class EnvironmentSensorRequest(
    val type: EnvironmentSensorRequestType,
    val sensorId: Int?,
    val firmwareFilename: String?,
    val firmwareBased64Encoded: String?,
    val sleepTimeInSeconds: Long?
)

data class EnvironmentSensorResponse(
    val sensors: List<EnvironmentSensorState>
)

data class EnvironmentSensorState(
    val sensorId: Int,
    val displayName: String,
    val sleepTimeInSeconds: Long,
    val sensorData: EnvironmentSensorData?,
    val firmwareUpgradeState: FirmwareUpgradeState?,
    val firmwareVersion: Int,
    val firmwareUpgradeScheduled: Boolean,
    val timeAdjustmentSchedule: Boolean
)

data class FirmwareUpgradeState(
    val firmwareSize: Int,
    val offsetRequested: Int,
    val timestampDelta: Long,
    val receivedAt: Long
)

data class EnvironmentSensorData(
    val temperature: Long,
    val humidity: Long,
    val batteryMilliVolts: Long,
    val adcLight: Long,
    val sleepTimeInSeconds: Long,
    val timestampDelta: Long,
    val receivedAt: Long
)

enum class EnvironmentSensorEventType {
    update
}

data class EnvironmentSensorEvent(
    val type: EnvironmentSensorEventType,
    val sensors: List<EnvironmentSensorState>
)
