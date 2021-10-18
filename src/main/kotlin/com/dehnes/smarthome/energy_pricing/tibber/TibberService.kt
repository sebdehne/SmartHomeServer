package com.dehnes.smarthome.energy_pricing.tibber

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService

class TibberService(
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    persistenceService: PersistenceService,
    private val influxDBClient: InfluxDBClient,
    executorService: ExecutorService
) : AbstractProcess(executorService, 60 * 5) {

    private val logger = KotlinLogging.logger { }
    private val tibberPriceClient = TibberPriceClient(objectMapper, persistenceService)
    private val tibberBackOffInMs = 60L * 60L * 1000L
    private var lastReload = 0L
    private var priceCache = listOf<Price>()

    @Synchronized
    override fun tickLocked(): Boolean {
        ensureCacheLoaded()

        return getCurrentPrice()?.let { price ->
            influxDBClient.recordSensorData(
                "energyPrice",
                listOf(
                    "price" to price.toString()
                ),
                "service" to "Tibber"
            )
            logger.info { "Recorded price=$price to influxDB" }
            true
        } ?: false
    }

    override fun logger() = logger

    @Synchronized
    fun mustWaitUntil(numberOfHoursRequired: Int): Instant? {
        ensureCacheLoaded()

        val now = Instant.now(clock)
        val nowPlus24Hours = now.plus(24, ChronoUnit.HOURS)

        val next24Hours = priceCache
            .sortedBy { it.from }
            .filter { it.to.isAfter(now) }
            .filter { it.from.isBefore(nowPlus24Hours) }

        val cheapEnoughHours = next24Hours
            .sortedBy { it.price }
            .let {
                if (it.size >= numberOfHoursRequired) {
                    it.subList(0, numberOfHoursRequired)
                } else
                    it
            }
            .sortedBy { it.from }

        val nextCheapHour = cheapEnoughHours.firstOrNull()
        return when {
            nextCheapHour == null -> {
                logger.info { "No cheap enough hour available" }
                null
            }
            nextCheapHour.isValidFor(now) -> {
                null
            }
            else -> nextCheapHour.from
        }

    }

    @Synchronized
    fun mustWaitUntilV2(skipPercentExpensiveHours: Int): Instant? {
        check(skipPercentExpensiveHours in 0..100)
        ensureCacheLoaded()
        val now = Instant.now(clock)

        val futurePrices = priceCache
            .sortedBy { it.from }
            .filter { it.to.isAfter(now) }

        val allowedHours = futurePrices
            .sortedBy { it.price }
            .let { sortedHours ->
                val keep = (100 - skipPercentExpensiveHours) * sortedHours.size / 100
                if (keep < sortedHours.size) {
                    sortedHours.subList(0, keep)
                } else {
                    sortedHours
                }
            }
            .sortedBy { it.from }

        val nextCheapHour = allowedHours.firstOrNull()
        return when {
            nextCheapHour == null -> {
                val endOfKnownPrices = priceCache.maxByOrNull { it.from }?.to
                logger.info { "No cheap enough hour available. Using endOfKnownPrices=$endOfKnownPrices" }
                endOfKnownPrices
            }
            nextCheapHour.isValidFor(now) -> {
                null
            }
            else -> nextCheapHour.from
        }

    }

    private fun getCurrentPrice(): Double? {
        ensureCacheLoaded()

        val now = Instant.now(clock)

        return priceCache.firstOrNull { price: Price -> price.isValidFor(now) }?.price
    }

    private fun ensureCacheLoaded() {
        if (lastReload + tibberBackOffInMs < System.currentTimeMillis()) {
            reloadCacheNow()
        }
    }

    private fun reloadCacheNow() {
        logger.info("Fetching tibber prices...")
        val prices = tibberPriceClient.getPrices()
        lastReload = System.currentTimeMillis()
        if (prices != null) {
            priceCache = prices
            logger.info("Fetching tibber prices...SUCCESS. " + objectMapper.writeValueAsString(prices))
        } else {
            logger.info("Fetching tibber prices...FAILED")
        }
    }
}