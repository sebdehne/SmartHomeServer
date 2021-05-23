import { FirmwareUpgradeState } from "./EnvironmentSensors";

export class GarageStatus {
    public lightIsOn: boolean;
    public doorStatus: DoorStatus;
    public autoCloseAfter: number | null;
    public timestampDelta: number;
    public firmwareVersion: number;
    public utcTimestampInMs: number;

    public constructor(lightIsOn: boolean, doorStatus: DoorStatus, autoCloseAfter: number | null, timestampDelta: number, firmwareVersion: number, utcTimestampInMs: number) {
        this.lightIsOn = lightIsOn;
        this.doorStatus = doorStatus;
        this.autoCloseAfter = autoCloseAfter;
        this.timestampDelta = timestampDelta;
        this.firmwareVersion = firmwareVersion;
        this.utcTimestampInMs = utcTimestampInMs;
    }
}

export enum DoorStatus {
    doorClosed = "doorClosed",
    doorOpen = "doorOpen",
    doorClosing = "doorClosing",
    doorOpening = "doorOpening",
    doorMiddle = "doorMiddle"
}

export class GarageRequest {
    public type: GarageRequestType;
    public garageDoorChangeAutoCloseDeltaInSeconds: number | null;
    public firmwareBased64Encoded: string | null;

    public constructor(type: GarageRequestType, garageDoorChangeAutoCloseDeltaInSeconds: number | null, firmwareBased64Encoded: string | null) {
        this.type = type;
        this.garageDoorChangeAutoCloseDeltaInSeconds = garageDoorChangeAutoCloseDeltaInSeconds;
        this.firmwareBased64Encoded = firmwareBased64Encoded;
    }
}

export class GarageResponse {
    public garageStatus: GarageStatus | null;
    public garageCommandSendSuccess: boolean | null;
    public garageCommandAdjustTimeSuccess: boolean | null;
    public firmwareUploadSuccess: boolean | null;
    public firmwareUpgradeState: FirmwareUpgradeState | null;

    public constructor(garageStatus: GarageStatus | null, garageCommandSendSuccess: boolean | null, garageCommandAdjustTimeSuccess: boolean | null, firmwareUploadSuccess: boolean | null, firmwareUpgradeState: FirmwareUpgradeState | null) {
        this.garageStatus = garageStatus;
        this.garageCommandSendSuccess = garageCommandSendSuccess;
        this.garageCommandAdjustTimeSuccess = garageCommandAdjustTimeSuccess;
        this.firmwareUploadSuccess = firmwareUploadSuccess;
        this.firmwareUpgradeState = firmwareUpgradeState;
    }
}

export enum GarageRequestType {
    openGarageDoor = "openGarageDoor",
    closeGarageDoor = "closeGarageDoor",

    getGarageStatus = "getGarageStatus",
    garageDoorExtendAutoClose = "garageDoorExtendAutoClose",
    adjustTime = "adjustTime",
    firmwareUpgrade = "firmwareUpgrade",
}
