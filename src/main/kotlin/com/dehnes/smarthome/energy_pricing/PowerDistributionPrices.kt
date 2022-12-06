package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.utils.DateTimeUtils
import java.time.Instant

object PowerDistributionPrices {

    val highPrice = 48.43
    val lowPrice = 38.80

    fun getPowerDistributionPriceInCents(time: Instant): Double =
        if (time.atZone(DateTimeUtils.zoneId).hour in 6 until 22) {
            highPrice
        } else {
            lowPrice
        }

}