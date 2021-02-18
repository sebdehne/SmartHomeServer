export class GarageStatus {
    public lightIsOn: boolean;
    public doorStatus: DoorStatus;
    public autoCloseAfter: number | null;
    public utcTimestampInMs: number;


    public constructor(lightIsOn: boolean, doorStatus: DoorStatus, autoCloseAfter: number | null, utcTimestampInMs: number) {
        this.lightIsOn = lightIsOn;
        this.doorStatus = doorStatus;
        this.autoCloseAfter = autoCloseAfter;
        this.utcTimestampInMs = utcTimestampInMs;
    }
}

export enum DoorStatus {
    doorClosed = "doorClosed",
    doorOpen = "doorOpen",
    doorClosing = "doorClosing",
    doorOpening = "doorOpening"
}

export class GarageRequest {
    public type: GarageRequestType;
    public garageDoorChangeAutoCloseDeltaInSeconds: number | null;

    public constructor(type: GarageRequestType, garageDoorChangeAutoCloseDeltaInSeconds: number | null) {
        this.type = type;
        this.garageDoorChangeAutoCloseDeltaInSeconds = garageDoorChangeAutoCloseDeltaInSeconds;
    }
}

export class GarageResponse {
    public garageStatus: GarageStatus | null;
    public garageCommandSendSuccess: boolean | null;


    public constructor(garageStatus: GarageStatus | null, garageCommandSendSuccess: boolean | null) {
        this.garageStatus = garageStatus;
        this.garageCommandSendSuccess = garageCommandSendSuccess;
    }
}

export enum GarageRequestType {
    openGarageDoor = "openGarageDoor",
    closeGarageDoor = "closeGarageDoor",

    getGarageStatus = "getGarageStatus",
    garageDoorExtendAutoClose = "garageDoorExtendAutoClose",
}
