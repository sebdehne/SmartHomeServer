export type EnergyPricingSettingsWrite = {
    service: string;
    neutralSpan?: number;
    avgMultiplier?: number;
}

export type EnergyPricingSettingsRead = {
    serviceConfigs: EnergyPriceConfig[],
}

export type EnergyPriceConfig = {
    service: string;
    neutralSpan: number;
    avgMultiplier: number;
    categorizedPrices: CategorizedPrice[];
    priceDecision?: PriceDecision;
}

export type CategorizedPrice = {
    category: PriceCategory;
    price: Price;
}

export type PriceCategory = 'cheap' | 'neutral' | 'expensive';

export type Price = {
    from: string;
    to: string;
    price: number;
}

export type PriceDecision = {
    current: PriceCategory;
    changesAt: string;
    changesInto: PriceCategory;
}
