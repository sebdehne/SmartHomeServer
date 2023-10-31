export type StairsHeatingType =
    'enableDisable' |
    'get' |
    'increaseTargetTemp' |
    'decreaseTargetTemp' |
    'increaseOutsideLowerTemp' |
    'decreaseOutsideLowerTemp' |
    'increaseOutsideUpperTemp' |
    'decreaseOutsideUpperTemp';


export type StairsHeatingRequest = {
    type: StairsHeatingType;
}

export type StairsHeatingResponse = {
    data?: StairsHeatingData;
    settings: StairsHeatingSettings;
}

export type StairsHeatingData = {
    currentState: boolean;
    temperature: number;
    current: number;
    createdAt: string;
}

export type StairsHeatingSettings = {
    outsideTemperatureRangeFrom: number;
    outsideTemperatureRangeTo: number;
    targetTemperature: number;
    enabled: boolean;
}