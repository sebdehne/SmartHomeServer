import { FirmwareUpgradeState } from "./EnvironmentSensors";

export type Mode = "ON" | "OFF" | "MANUAL";

export type UnderFloorHeaterRequest = {
    type: UnderFloorHeaterRequestType;
    newMode?: UnderFloorHeaterMode
    newTargetTemperature?: number;
    skipPercentExpensiveHours?: number;
    firmwareBased64Encoded?: string;
}

export type UnderFloorHeaterResponse = {
    underFloorHeaterStatus: UnderFloorHeaterStatus;
    updateUnderFloorHeaterModeSuccess?: boolean;
    adjustTimeSuccess?: boolean;
    firmwareUploadSuccess?: boolean;
    firmwareUpgradeState?: FirmwareUpgradeState;
}

export type UnderFloorHeaterRequestType =
    "getStatus"
    | "updateMode"
    | "updateTargetTemperature"
    | "setSkipPercentExpensiveHours"
    | "adjustTime"
    | "firmwareUpgrade";

export type UnderFloorHeaterStatus = {
    mode: UnderFloorHeaterMode;
    status: OnOff;
    targetTemperature: number;
    skipPercentExpensiveHours: number;
    waitUntilCheapHour?: number;
    timestampDelta: number;
    fromController?: UnderFloorHeaterStatusFromController;
}

export type UnderFloorHeaterStatusFromController = {
    receivedAt: number;
    currentTemperature: number;
    temperatureError: number;
}

export type UnderFloorHeaterMode = "permanentOn" | "permanentOff" | "constantTemperature";

export type OnOff = "on" | "off";
