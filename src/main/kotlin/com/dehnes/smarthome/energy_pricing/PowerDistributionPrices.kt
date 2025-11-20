package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.utils.DateTimeUtils
import java.time.Instant

object PowerDistributionPrices {

    val highPrice = 42.24
    val lowPrice = 33.52

    fun getPowerDistributionPriceNok(time: Instant): Double =
        if (time.atZone(DateTimeUtils.zoneId).hour in 6 until 22) {
            highPrice / 100
        } else {
            lowPrice / 100
        }

}