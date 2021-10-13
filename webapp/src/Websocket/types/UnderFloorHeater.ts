import { FirmwareUpgradeState } from "./EnvironmentSensors";

export enum Mode {
    ON = "ON",
    OFF = "OFF",
    MANUAL = "MANUAL"
}

export class UnderFloorHeaterRequest {
    public type: UnderFloorHeaterRequestType;
    public newMode: UnderFloorHeaterMode | null;
    public newTargetTemperature: number | null;
    public newMostExpensiveHoursToSkip: number | null;
    public firmwareBased64Encoded: string | null;

    public constructor(type: UnderFloorHeaterRequestType, newMode: UnderFloorHeaterMode | null, newTargetTemperature: number | null, newMostExpensiveHoursToSkip: number | null, firmwareBased64Encoded: string | null) {
        this.type = type;
        this.newMode = newMode;
        this.newTargetTemperature = newTargetTemperature;
        this.newMostExpensiveHoursToSkip = newMostExpensiveHoursToSkip;
        this.firmwareBased64Encoded = firmwareBased64Encoded;
    }
}

export class UnderFloorHeaterResponse {
    public underFloorHeaterStatus: UnderFloorHeaterStatus;
    public updateUnderFloorHeaterModeSuccess: boolean | null;
    public adjustTimeSuccess: boolean | null;
    public firmwareUploadSuccess: boolean | null;
    public firmwareUpgradeState: FirmwareUpgradeState | null;

    public constructor(underFloorHeaterStatus: UnderFloorHeaterStatus, updateUnderFloorHeaterModeSuccess: boolean | null, adjustTimeSuccess: boolean | null, firmwareUploadSuccess: boolean | null, firmwareUpgradeState: FirmwareUpgradeState | null) {
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.updateUnderFloorHeaterModeSuccess = updateUnderFloorHeaterModeSuccess;
        this.adjustTimeSuccess = adjustTimeSuccess;
        this.firmwareUploadSuccess = firmwareUploadSuccess;
        this.firmwareUpgradeState = firmwareUpgradeState;
    }
}

export enum UnderFloorHeaterRequestType {
    getStatus = "getStatus",
    updateMode = "updateMode",
    updateTargetTemperature = "updateTargetTemperature",
    updateMostExpensiveHoursToSkip = "updateMostExpensiveHoursToSkip",
    adjustTime = "adjustTime",
    firmwareUpgrade = "firmwareUpgrade"
}

export class UnderFloorHeaterStatus {
    public mode: UnderFloorHeaterMode;
    public status: OnOff;
    public targetTemperature: number;
    public mostExpensiveHoursToSkip: number;
    public waitUntilCheapHour: number | null;
    public timestampDelta: number;
    public fromController: UnderFloorHeaterStatusFromController | null;

    public constructor(mode: UnderFloorHeaterMode, status: OnOff, targetTemperature: number, mostExpensiveHoursToSkip: number, waitUntilCheapHour: number | null, timestampDelta: number, fromController: UnderFloorHeaterStatusFromController | null) {
        this.mode = mode;
        this.status = status;
        this.targetTemperature = targetTemperature;
        this.mostExpensiveHoursToSkip = mostExpensiveHoursToSkip;
        this.waitUntilCheapHour = waitUntilCheapHour;
        this.timestampDelta = timestampDelta;
        this.fromController = fromController;
    }
}

export class UnderFloorHeaterStatusFromController {
    public receivedAt: number;
    public currentTemperature: number;
    public temperatureError: number;


    public constructor(receivedAt: number, currentTemperature: number, temperatureError: number) {
        this.receivedAt = receivedAt;
        this.currentTemperature = currentTemperature;
        this.temperatureError = temperatureError;
    }
}

export enum UnderFloorHeaterMode {
    permanentOn = "permanentOn",
    permanentOff = "permanentOff",
    constantTemperature = "constantTemperature"
}

export enum OnOff {
    on = "on",
    off = "off"
}
