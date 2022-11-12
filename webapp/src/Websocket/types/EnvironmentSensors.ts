export type EnvironmentSensorRequestType =
    "getAllEnvironmentSensorData"
    | "uploadFirmware"
    | "scheduleTimeAdjustment"
    | "cancelTimeAdjustment"
    | "scheduleFirmwareUpgrade"
    | "cancelFirmwareUpgrade"
    | "adjustSleepTimeInSeconds"
    | "scheduleReset"
    | "cancelReset";

export type EnvironmentSensorRequest = {
    type: EnvironmentSensorRequestType;
    sensorId?: number;
    firmwareFilename?: string;
    firmwareBased64Encoded?: string;
    sleepTimeInSecondsDelta?: number;
}

export type FirmwareUpgradeState = {
    firmwareSize: number;
    offsetRequested: number;
    timestampDelta: number;
    receivedAt: number;
    rssi: number;
}

export type EnvironmentSensorData = {
    temperature: number;
    temperatureError: boolean;
    humidity: number;
    batteryMilliVolts: number;
    adcLight: number;
    sleepTimeInSeconds: number;
    timestampDelta: number;
    receivedAt: number;
    rssi: number;
}

export type EnvironmentSensorState = {
    sensorId: number;
    displayName: string;
    sleepTimeInSeconds: number;
    sensorData?: EnvironmentSensorData;
    firmwareUpgradeState?: FirmwareUpgradeState;
    firmwareVersion: number;
    firmwareUpgradeScheduled: boolean;
    resetScheduled: boolean;
    timeAdjustmentSchedule: boolean;
}

export type FirmwareInfo = {
    filename: string;
    size: number;
}


export type EnvironmentSensorResponse = {
    sensors: EnvironmentSensorState[];
    firmwareInfo?: FirmwareInfo;
}

export type EnvironmentSensorEventType = "update"

export type EnvironmentSensorEvent = {
    type: EnvironmentSensorEventType;
    sensors: EnvironmentSensorState[];
    firmwareInfo?: FirmwareInfo;
}

