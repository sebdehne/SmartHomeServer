export type EnergyPricingSettingsWrite = {
    pricingThreshold?: number;
    serviceToSkipPercentExpensiveHours?: any
}

export type EnergyPricingSettingsRead = {
    serviceToSkipPercentExpensiveHours: EnergyPriceConfig[],
    pricingThreshold: number
}

export type EnergyPriceConfig = {
    service: string;
    skipPercentExpensiveHours: number,
    mustWaitUntil?: string
}