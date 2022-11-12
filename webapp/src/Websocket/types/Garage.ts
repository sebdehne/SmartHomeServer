import { FirmwareUpgradeState } from "./EnvironmentSensors";

export type GarageStatus = {
    lightIsOn: boolean;
    doorStatus: DoorStatus;
    autoCloseAfter?: number;
    timestampDelta: number;
    firmwareVersion: number;
    utcTimestampInMs: number;
}

export type DoorStatus = "doorClosed" | "doorOpen" | "doorClosing";

export type GarageRequest = {
    type: GarageRequestType;
    garageDoorChangeAutoCloseDeltaInSeconds?: number;
    firmwareBased64Encoded?: string;
}

export type GarageResponse = {
    garageStatus?: GarageStatus;
    garageCommandSendSuccess?: boolean;
    garageCommandAdjustTimeSuccess?: boolean;
    firmwareUploadSuccess?: boolean;
    firmwareUpgradeState?: FirmwareUpgradeState;
}

export type GarageRequestType =
    "openGarageDoor"
    | "closeGarageDoor"
    | "getGarageStatus"
    | "garageDoorExtendAutoClose"
    | "adjustTime"
    | "firmwareUpgrade";
