package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.energy_pricing.EnergyPriceConfig

data class EnergyPricingSettingsRead(
    val serviceConfigs: List<EnergyPriceConfig>,
)

data class EnergyPricingSettingsWrite(
    val service: String,
    val neutralSpan: Double?,
    val avgMultiplier: Double?,
)
