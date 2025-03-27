export type GarageLightStatus = {
    ceilingLightIsOn: boolean;
    ledStripeStatus: LEDStripeStatus;
    utcTimestampInMs: number;
    ledStripeLowMillivolts: number;
    ledStripeCurrentMode: LightLedMode,
}

export type LEDStripeStatus = 'off' | 'onLow' | 'onHigh';

export type GarageLightRequest = {
    type: GarageLightRequestType;
    ledStripeLowMillivolts?: number;
    setLedStripeMode?: LightLedMode;
}

export enum LightLedMode {
    auto = 'auto',
    manual = 'manual',
}

export type GarageLightResponse = {
    status?: GarageLightStatus;
    commandSendSuccess?: boolean;
}

export type GarageLightRequestType =
    "switchOnCeilingLight"
    | "switchOffCeilingLight"
    | "switchLedStripeOff"
    | "switchLedStripeOnLow"
    | "switchLedStripeOnHigh"
    | "getStatus"
    | "setLedStripeLowMillivolts"
    | "setLedStripeMode";

export type GarageVentilationState = {
    milliVolts: number;
    createdAt: string;
}