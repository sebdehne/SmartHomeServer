export enum EnvironmentSensorRequestType {
    getAllEnvironmentSensorData = "getAllEnvironmentSensorData",
    uploadFirmware = "uploadFirmware",

    scheduleTimeAdjustment = "scheduleTimeAdjustment",
    cancelTimeAdjustment = "cancelTimeAdjustment",
    scheduleFirmwareUpgrade = "scheduleFirmwareUpgrade",
    cancelFirmwareUpgrade = "cancelFirmwareUpgrade",
    adjustSleepTimeInSeconds = "adjustSleepTimeInSeconds",
    scheduleReset = "scheduleReset",
    cancelReset = "cancelReset"
}

export class EnvironmentSensorRequest {
    public type: EnvironmentSensorRequestType;
    public sensorId: number | null;
    public firmwareFilename: string | null;
    public firmwareBased64Encoded: string | null;
    public sleepTimeInSecondsDelta: number | null;

    public constructor(type: EnvironmentSensorRequestType, sensorId: number | null, firmwareFilename: string | null, firmwareBased64Encoded: string | null, sleepTimeInSecondsDelta: number | null) {
        this.type = type;
        this.sensorId = sensorId;
        this.firmwareFilename = firmwareFilename;
        this.firmwareBased64Encoded = firmwareBased64Encoded;
        this.sleepTimeInSecondsDelta = sleepTimeInSecondsDelta;
    }
}

export class FirmwareUpgradeState {
    public firmwareSize: number;
    public offsetRequested: number;
    public timestampDelta: number;
    public receivedAt: number;
    public rssi: number;

    public constructor(firmwareSize: number, offsetRequested: number, timestampDelta: number, receivedAt: number, rssi: number) {
        this.firmwareSize = firmwareSize;
        this.offsetRequested = offsetRequested;
        this.timestampDelta = timestampDelta;
        this.receivedAt = receivedAt;
        this.rssi = rssi;
    }
}

export class EnvironmentSensorData {
    public temperature: number;
    public temperatureError: boolean;
    public humidity: number;
    public batteryMilliVolts: number;
    public adcLight: number;
    public sleepTimeInSeconds: number;
    public timestampDelta: number;
    public receivedAt: number;
    public rssi: number;

    public constructor(temperature: number, temperatureError: boolean, humidity: number, batteryMilliVolts: number, adcLight: number, sleepTimeInSeconds: number, timestampDelta: number, receivedAt: number, rssi: number) {
        this.temperature = temperature;
        this.temperatureError = temperatureError;
        this.humidity = humidity;
        this.batteryMilliVolts = batteryMilliVolts;
        this.adcLight = adcLight;
        this.sleepTimeInSeconds = sleepTimeInSeconds;
        this.timestampDelta = timestampDelta;
        this.receivedAt = receivedAt;
        this.rssi = rssi;
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
    public resetScheduled: boolean;
    public timeAdjustmentSchedule: boolean;


   public constructor(sensorId: number, displayName: string, sleepTimeInSeconds: number, sensorData: EnvironmentSensorData | null, firmwareUpgradeState: FirmwareUpgradeState | null, firmwareVersion: number, firmwareUpgradeScheduled: boolean, resetScheduled: boolean, timeAdjustmentSchedule: boolean) {
        this.sensorId = sensorId;
        this.displayName = displayName;
        this.sleepTimeInSeconds = sleepTimeInSeconds;
        this.sensorData = sensorData;
        this.firmwareUpgradeState = firmwareUpgradeState;
        this.firmwareVersion = firmwareVersion;
        this.firmwareUpgradeScheduled = firmwareUpgradeScheduled;
        this.resetScheduled = resetScheduled;
        this.timeAdjustmentSchedule = timeAdjustmentSchedule;
    }
}

export class FirmwareInfo{
    public filename: string;
    public size: number;

    public constructor(filename: string, size: number) {
        this.filename = filename;
        this.size = size;
    }
}


export class EnvironmentSensorResponse {
    public sensors: EnvironmentSensorState[];
    public firmwareInfo: FirmwareInfo | null;

    public constructor(sensors: EnvironmentSensorState[], firmwareInfo: FirmwareInfo | null) {
        this.sensors = sensors;
        this.firmwareInfo = firmwareInfo;
    }
}

export enum EnvironmentSensorEventType {
    update = "update"
}

export class EnvironmentSensorEvent {
    public type: EnvironmentSensorEventType;
    public sensors: EnvironmentSensorState[];
    public firmwareInfo: FirmwareInfo | null;

    public constructor(type: EnvironmentSensorEventType, sensors: EnvironmentSensorState[], firmwareInfo: FirmwareInfo | null) {
        this.type = type;
        this.sensors = sensors;
        this.firmwareInfo = firmwareInfo;
    }
}

