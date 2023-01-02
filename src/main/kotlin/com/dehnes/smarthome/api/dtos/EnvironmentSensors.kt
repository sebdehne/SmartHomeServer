package com.dehnes.smarthome.api.dtos

enum class EnvironmentSensorRequestType {
    getAllEnvironmentSensorData,
    uploadFirmware,

    scheduleTimeAdjustment,
    cancelTimeAdjustment,
    scheduleFirmwareUpgrade,
    cancelFirmwareUpgrade,
    adjustSleepTimeInSeconds,
    scheduleReset,
    cancelReset
}

data class EnvironmentSensorRequest(
    val type: EnvironmentSensorRequestType,
    val sensorId: Int?,
    val firmwareFilename: String?,
    val firmwareBased64Encoded: String?,
    val sleepTimeInSecondsDelta: Int?
)

data class EnvironmentSensorResponse(
    val sensors: List<EnvironmentSensorState>,
    val firmwareInfo: FirmwareInfo?
)

data class FirmwareInfo(
    val filename: String,
    val size: Int
)

data class EnvironmentSensorState(
    val sensorId: Int,
    val displayName: String,
    val sleepTimeInSeconds: Int,
    val sensorData: EnvironmentSensorData?,
    val firmwareUpgradeState: FirmwareUpgradeState?,
    val firmwareVersion: Int,
    val firmwareUpgradeScheduled: Boolean,
    val resetScheduled: Boolean,
    val timeAdjustmentSchedule: Boolean
)

data class FirmwareUpgradeState(
    val firmwareSize: Int,
    val offsetRequested: Int,
    val timestampDelta: Long,
    val receivedAt: Long,
    val rssi: Int
)

data class EnvironmentSensorData(
    val temperature: Long,
    val temperatureError: Boolean,
    val humidity: Long,
    val batteryMilliVolts: Long,
    val adcLight: Long,
    val sleepTimeInSeconds: Int,
    val timestampDelta: Long,
    val receivedAt: Long,
    val rssi: Int
)

enum class EnvironmentSensorEventType {
    update
}

data class EnvironmentSensorEvent(
    val type: EnvironmentSensorEventType,
    val sensors: List<EnvironmentSensorState>,
    val firmwareInfo: FirmwareInfo?
)
