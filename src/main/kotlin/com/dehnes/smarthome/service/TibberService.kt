package com.dehnes.smarthome.service

import com.dehnes.smarthome.external.Price
import com.dehnes.smarthome.external.TibberPriceClient
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TibberService(
    private val clock: Clock,
    private val tibberPriceClient: TibberPriceClient
) {

    private val logger = KotlinLogging.logger { }

    private val tibberBackOffInMs = 60L * 60L * 1000L
    private var lastReload = 0L
    private var priceCache = listOf<Price>()

    @Synchronized
    fun isEnergyPriceOK(numberOfHoursRequired: Int): Boolean {
        if (lastReload + tibberBackOffInMs < System.currentTimeMillis()) {
            reloadCacheNow()
        }
        val now = Instant.now(clock)
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val todaysPrices: List<Price> = priceCache
            .filter { price: Price -> price.isValidForDay(today) }
            .sortedBy { p -> p.price }

        return if (todaysPrices.isEmpty()) {
            true
        } else todaysPrices.subList(0, numberOfHoursRequired).any { p: Price ->
            p.isValidFor(now)
        }
    }

    private fun reloadCacheNow() {
        logger.info("Fetching tibber prices...")
        val prices = tibberPriceClient.getPrices()
        lastReload = System.currentTimeMillis()
        if (prices != null) {
            priceCache = prices
            logger.info("Fetching tibber prices...SUCCESS")
        } else {
            logger.info("Fetching tibber prices...FAILED")
        }
    }
}