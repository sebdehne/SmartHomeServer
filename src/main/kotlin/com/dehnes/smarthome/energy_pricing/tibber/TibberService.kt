package com.dehnes.smarthome.energy_pricing.tibber

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
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

        influxDBClient.recordSensorData(
            priceCache
                .sortedBy { it.from }
                .flatMap { p ->
                    p.toInfluxDbRecords()
                }
        )

        return true
    }

    override fun logger() = logger

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