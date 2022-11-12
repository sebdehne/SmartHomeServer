export type OperationMode = "automatic" | "passthrough" | "manual";
export type ProfileType = "autoCharging" | "autoDischarging" | "passthrough" | "manual"
export type ProfileSettings = {
    profileType: ProfileType;
    acPowerSetPoint: number;
    maxChargePower: number;
    maxDischargePower: number;
}
export type ESSState = {
    measurements: ESSValues;
    operationMode: OperationMode;
    currentProfile: ProfileType;
    soCLimit: SoCLimit;
    profileSettings: ProfileSettings[];
}

export type ESSWrite = {
    operationMode?: OperationMode,
    updateProfile?: ProfileSettings,
    soCLimit?: SoCLimit,
}

export type SoCLimit = {
    from: number;
    to: number;
}

export type ESSValues = {
    soc: number;
    batteryCurrent: number;
    batteryPower: number;
    batteryVoltage: number;

    gridL1: GridData;
    gridL2: GridData;
    gridL3: GridData;
    gridPower: number;

    outputL1: GridData;
    outputL2: GridData;
    outputL3: GridData;
    outputPower: number;

    systemState: string;
    mode: string;

    inverterAlarms: string[];
    batteryAlarm: number,
    batteryAlarms: string[];
}

export type GridData = {
    current: number;
    power: number;
    freq: number;
    voltage: number;
}