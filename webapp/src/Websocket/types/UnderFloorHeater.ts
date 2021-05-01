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

    public constructor(type: UnderFloorHeaterRequestType, newMode: UnderFloorHeaterMode | null, newTargetTemperature: number | null, newMostExpensiveHoursToSkip: number | null) {
        this.type = type;
        this.newMode = newMode;
        this.newTargetTemperature = newTargetTemperature;
        this.newMostExpensiveHoursToSkip = newMostExpensiveHoursToSkip;
    }
}

export class UnderFloorHeaterResponse {
    public underFloorHeaterStatus: UnderFloorHeaterStatus;
    public updateUnderFloorHeaterModeSuccess: boolean | null;

    public constructor(underFloorHeaterStatus: UnderFloorHeaterStatus, updateUnderFloorHeaterModeSuccess: boolean | null) {
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.updateUnderFloorHeaterModeSuccess = updateUnderFloorHeaterModeSuccess;
    }
}

export enum UnderFloorHeaterRequestType {
    getStatus = "getStatus",
    updateMode = "updateMode",
    updateTargetTemperature = "updateTargetTemperature",
    updateMostExpensiveHoursToSkip = "updateMostExpensiveHoursToSkip"
}

export class UnderFloorHeaterStatus {
    public mode: UnderFloorHeaterMode;
    public status: OnOff;
    public targetTemperature: number;
    public mostExpensiveHoursToSkip: number;
    public waitUntilCheapHour: number | null;
    public fromController: UnderFloorHeaterStatusFromController | null;

    public constructor(mode: UnderFloorHeaterMode, status: OnOff, targetTemperature: number, mostExpensiveHoursToSkip: number, waitUntilCheapHour: number | null, fromController: UnderFloorHeaterStatusFromController | null) {
        this.mode = mode;
        this.status = status;
        this.targetTemperature = targetTemperature;
        this.mostExpensiveHoursToSkip = mostExpensiveHoursToSkip;
        this.waitUntilCheapHour = waitUntilCheapHour;
        this.fromController = fromController;
    }
}

export class UnderFloorHeaterStatusFromController {
    public receivedAt: number;
    public currentTemperature: number;


    public constructor(receivedAt: number, currentTemperature: number) {
        this.receivedAt = receivedAt;
        this.currentTemperature = currentTemperature;
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
