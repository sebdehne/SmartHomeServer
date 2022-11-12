package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ExecutorService

interface PriceSource {
    fun getPrices(): List<Price>?
}

private const val skipPercentExpensiveHoursPrefix = "EnergyPriceService.skipPercentExpensiveHours."

const val serviceEnergyStorage = "EnergyStorage"

class EnergyPriceService(
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    private val priceSource: PriceSource,
    private val influxDBClient: InfluxDBClient,
    executorService: ExecutorService,
    private val persistenceService: PersistenceService,
) : AbstractProcess(executorService, 60 * 5) {

    private val logger = KotlinLogging.logger { }
    private val backOffInMs = 60L * 60L * 1000L
    private var lastReload = 0L
    private var priceCache = listOf<Price>()

    var gridIsOffline: Boolean = false

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
    fun getCachedPrices(): List<Price> {
        ensureCacheLoaded()
        return priceCache
    }

    fun getAllSettings(): List<EnergyPriceConfig> = persistenceService.getAllFor(skipPercentExpensiveHoursPrefix).map {
        val service = it.first.replace(skipPercentExpensiveHoursPrefix, "")
        EnergyPriceConfig(
            service,
            it.second.toInt(),
            mustWaitUntilV2(service)
        )
    }

    fun getSkipPercentExpensiveHours(serviceType: String): Int =
        persistenceService["$skipPercentExpensiveHoursPrefix$serviceType", "100"]!!.toInt()

    fun setSkipPercentExpensiveHours(serviceType: String, skipPercentExpensiveHours: Int?) {
        if (skipPercentExpensiveHours == null) {
            persistenceService["$skipPercentExpensiveHoursPrefix$serviceType"] = null
        } else {
            check(skipPercentExpensiveHours in 0..100)
            persistenceService["$skipPercentExpensiveHoursPrefix$serviceType"] = skipPercentExpensiveHours.toString()
        }
    }

    fun getPricingThreshold() = persistenceService["EnergyPriceService.pricingThreshold", "1.0"]!!.toDouble()
    fun setPricingThreshold(threshold: Double) {
        persistenceService["EnergyPriceService.pricingThreshold"] = threshold.toString()
    }

    @Synchronized
    fun mustWaitUntilV2(serviceType: String): Instant? {
        val skipPercentExpensiveHours = getSkipPercentExpensiveHours(serviceType)
        check(skipPercentExpensiveHours in 0..100)
        ensureCacheLoaded()
        val now = Instant.now(clock)

        val currentPrice = priceCache.firstOrNull { it.isValidFor(now) }
        val pricingThreshold = getPricingThreshold()
        if (currentPrice != null && currentPrice.price < pricingThreshold) {
            logger.info { "Current price ${currentPrice.price} is lower than threshold $pricingThreshold" }
            return null
        }

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
        if (lastReload + backOffInMs < System.currentTimeMillis()) {
            reloadCacheNow()
        }
    }

    private fun reloadCacheNow() {
        logger.info("Fetching energy prices...")
        val prices = priceSource.getPrices()
        lastReload = System.currentTimeMillis()
        if (!prices.isNullOrEmpty()) {
            priceCache = prices
            logger.info("Fetching energy prices...SUCCESS. " + objectMapper.writeValueAsString(prices))
        } else {
            logger.info("Fetching energy prices...FAILED")
        }
    }
}

data class EnergyPriceConfig(
    val service: String,
    val skipPercentExpensiveHours: Int,
    val mustWaitUntil: Instant?
)
