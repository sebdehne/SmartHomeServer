export enum Mode {
    ON = "ON",
    OFF = "OFF",
    MANUAL = "MANUAL"
}

export class UnderFloorHeaterRequest {
    public type: UnderFloorHeaterRequestType;
    public updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode | null;


    public constructor(type: UnderFloorHeaterRequestType, updateUnderFloorHeaterMode: UpdateUnderFloorHeaterMode | null) {
        this.type = type;
        this.updateUnderFloorHeaterMode = updateUnderFloorHeaterMode;
    }
}

export class UnderFloorHeaterResponse {
    public underFloorHeaterStatus: UnderFloorHeaterStatus | null;
    public updateUnderFloorHeaterModeSuccess: boolean | null;


    public constructor(underFloorHeaterStatus: UnderFloorHeaterStatus | null, updateUnderFloorHeaterModeSuccess: boolean | null) {
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.updateUnderFloorHeaterModeSuccess = updateUnderFloorHeaterModeSuccess;
    }
}

export enum UnderFloorHeaterRequestType {
    updateUnderFloorHeaterMode = "updateUnderFloorHeaterMode",
    getUnderFloorHeaterStatus = "getUnderFloorHeaterStatus"
}

export class UnderFloorHeaterStatus {
    public mode: UnderFloorHeaterMode;
    public status: OnOff;
    public currentTemperature: number;
    public constantTemperatureStatus: UnderFloorHeaterConstantTemperaturStatus;
    public utcTimestampInMs: number;

    public constructor(mode: UnderFloorHeaterMode, status: OnOff, currentTemperature: number, constantTemperatureStatus: UnderFloorHeaterConstantTemperaturStatus, utcTimestampInMs: number) {
        this.mode = mode;
        this.status = status;
        this.currentTemperature = currentTemperature;
        this.constantTemperatureStatus = constantTemperatureStatus;
        this.utcTimestampInMs = utcTimestampInMs;
    }
}

export class UpdateUnderFloorHeaterMode {
    public newMode: UnderFloorHeaterMode;
    public newTargetTemperature: number | null;
    public newMostExpensiveHoursToSkip: number | null;


    public constructor(newMode: UnderFloorHeaterMode, newTargetTemperature: number | null, newMostExpensiveHoursToSkip: number | null) {
        this.newMode = newMode;
        this.newTargetTemperature = newTargetTemperature;
        this.newMostExpensiveHoursToSkip = newMostExpensiveHoursToSkip;
    }
}

export enum UnderFloorHeaterMode {
    permanentOn = "permanentOn",
    permanentOff = "permanentOff",
    constantTemperature = "constantTemperature"
}

export class UnderFloorHeaterConstantTemperaturStatus {
    public targetTemperature: number;
    public mostExpensiveHoursToSkip: number;
    public waitUntilCheapHour: number | null;

    public constructor(targetTemperature: number, mostExpensiveHoursToSkip: number, waitUntilCheapHour: number | null) {
        this.targetTemperature = targetTemperature;
        this.mostExpensiveHoursToSkip = mostExpensiveHoursToSkip;
        this.waitUntilCheapHour = waitUntilCheapHour;
    }
}

export enum OnOff {
    on = "on",
    off = "off"
}
