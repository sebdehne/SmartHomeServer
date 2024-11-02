export type GarageLightStatus = {
    ceilingLightIsOn: boolean;
    ledStripeStatus: LEDStripeStatus;
    timestampDelta: number;
    utcTimestampInMs: number;
}

export type LEDStripeStatus = 'off' | 'onLow' | 'onHigh';

export type GarageLightRequest = {
    type: GarageLightRequestType;
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
    | "getStatus";
