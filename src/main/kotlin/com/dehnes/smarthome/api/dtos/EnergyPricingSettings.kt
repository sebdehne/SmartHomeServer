package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.energy_pricing.EnergyPriceConfig

data class EnergyPricingSettingsRead(
    val serviceToSkipPercentExpensiveHours: List<EnergyPriceConfig>,
    val pricingThreshold: Double
)

data class EnergyPricingSettingsWrite(
    val pricingThreshold: Double? = null,
    val serviceToSkipPercentExpensiveHours: Map<String, Int> = emptyMap(),
)
