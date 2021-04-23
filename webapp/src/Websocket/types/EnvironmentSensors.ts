export enum EnvironmentSensorRequestType {
    getAllEnvironmentSensorData = "getAllEnvironmentSensorData",
    uploadFirmware = "uploadFirmware",

    scheduleTimeAdjustment = "scheduleTimeAdjustment",
    cancelTimeAdjustment = "cancelTimeAdjustment",
    scheduleFirmwareUpgrade = "scheduleFirmwareUpgrade",
    cancelFirmwareUpgrade = "cancelFirmwareUpgrade",
    adjustSleepTimeInSeconds = "adjustSleepTimeInSeconds"
}

export class EnvironmentSensorRequest {
    public type: EnvironmentSensorRequestType;
    public sensorId: number | null;
    public firmwareFilename: string | null;
    public firmwareBased64Encoded: string | null;
    public sleepTimeInSeconds: number | null;

    public constructor(type: EnvironmentSensorRequestType, sensorId: number | null, firmwareFilename: string | null, firmwareBased64Encoded: string | null, sleepTimeInSeconds: number | null) {
        this.type = type;
        this.sensorId = sensorId;
        this.firmwareFilename = firmwareFilename;
        this.firmwareBased64Encoded = firmwareBased64Encoded;
        this.sleepTimeInSeconds = sleepTimeInSeconds;
    }
}

export class FirmwareUpgradeState {
    public firmwareSize: number;
    public offsetRequested: number;
    public timestampDelta: number;
    public receivedAt: number;

    public constructor(firmwareSize: number, offsetRequested: number, timestampDelta: number, receivedAt: number) {
        this.firmwareSize = firmwareSize;
        this.offsetRequested = offsetRequested;
        this.timestampDelta = timestampDelta;
        this.receivedAt = receivedAt;
    }
}

export class EnvironmentSensorData {
    public temperature: number;
    public humidity: number;
    public batteryMilliVolts: number;
    public adcLight: number;
    public sleepTimeInSeconds: number;
    public timestampDelta: number;
    public receivedAt: number;


    public constructor(temperature: number, humidity: number, batteryMilliVolts: number, adcLight: number, sleepTimeInSeconds: number, timestampDelta: number, receivedAt: number) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.batteryMilliVolts = batteryMilliVolts;
        this.adcLight = adcLight;
        this.sleepTimeInSeconds = sleepTimeInSeconds;
        this.timestampDelta = timestampDelta;
        this.receivedAt = receivedAt;
    }
}

export class EnvironmentSensorState {
    public sensorId: number;
    public displayName: string;
    public sleepTimeInSeconds: number;
    public sensorData: EnvironmentSensorData | null;
    public firmwareUpgradeState: FirmwareUpgradeState | null;
    public firmwareVersion: number;
    public firmwareUpgradeScheduled: boolean;
    public timeAdjustmentSchedule: boolean;

    public constructor(sensorId: number, displayName: string, sleepTimeInSeconds: number, sensorData: EnvironmentSensorData | null, firmwareUpgradeState: FirmwareUpgradeState | null, firmwareVersion: number, firmwareUpgradeScheduled: boolean, timeAdjustmentSchedule: boolean) {
        this.sensorId = sensorId;
        this.displayName = displayName;
        this.sleepTimeInSeconds = sleepTimeInSeconds;
        this.sensorData = sensorData;
        this.firmwareUpgradeState = firmwareUpgradeState;
        this.firmwareVersion = firmwareVersion;
        this.firmwareUpgradeScheduled = firmwareUpgradeScheduled;
        this.timeAdjustmentSchedule = timeAdjustmentSchedule;
    }
}

export class EnvironmentSensorResponse {
    public sensors: EnvironmentSensorState[];

    public constructor(sensors: EnvironmentSensorState[]) {
        this.sensors = sensors;
    }
}

export enum EnvironmentSensorEventType {
    update = "update"
}

export class EnvironmentSensorEvent {
    public type: EnvironmentSensorEventType;
    public sensors: EnvironmentSensorState[];

    public constructor(type: EnvironmentSensorEventType, sensors: EnvironmentSensorState[]) {
        this.type = type;
        this.sensors = sensors;
    }
}

