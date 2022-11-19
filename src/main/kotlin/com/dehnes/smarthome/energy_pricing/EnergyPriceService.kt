package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.DateTimeUtils
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ExecutorService

interface PriceSource {
    fun getPrices(): List<Price>?
}

const val serviceEnergyStorage = "EnergyStorage"

class EnergyPriceService(
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

    fun getAllSettings(): List<EnergyPriceConfig> = persistenceService.getAllFor("EnergyPriceService.neutralSpan.").map {
        val service = it.first.replace("EnergyPriceService.neutralSpan.", "")
        val today = Instant.now().atZone(DateTimeUtils.zoneId).toLocalDate()
        val categorizedPrices = listOf(
            today.minusDays(1),
            today,
            today.plusDays(1),
        )
            .map { findSuitablePrices(service, it) }
            .flatten()
        EnergyPriceConfig(
            service,
            getNeutralSpan(service),
            getAvgMultiplier(service),
            categorizedPrices,
            categorizedPrices.priceDecision()
        )
    }

    fun getNeutralSpan(serviceType: String) =
        persistenceService["EnergyPriceService.neutralSpan.$serviceType", "0.4"]!!.toDouble()

    fun setNeutralSpan(serviceType: String, neutralSpan: Double) {
        persistenceService["EnergyPriceService.neutralSpan.$serviceType"] = neutralSpan.toString()
    }

    fun getAvgMultiplier(serviceType: String) =
        persistenceService["EnergyPriceService.avgMultiplier.$serviceType", "0"]!!.toDouble()

    fun setAvgMultiplier(serviceType: String, avgMultiplier: Double) {
        persistenceService["EnergyPriceService.avgMultiplier.$serviceType"] = avgMultiplier.toString()
    }

    @Synchronized
    fun findSuitablePrices(serviceType: String, day: LocalDate): List<CategorizedPrice> {
        ensureCacheLoaded()

        val periodFrom = day.atStartOfDay(DateTimeUtils.zoneId).toInstant()
        val periodUntil = (day.plusDays(1)).atStartOfDay(DateTimeUtils.zoneId).toInstant()

        val priceRange = priceCache
            .sortedBy { it.from }
            .filter { it.from >= periodFrom }
            .filter { it.to <= periodUntil }

        if (priceRange.isEmpty()) return emptyList()

        val avgRaw = priceRange.map { it.price }.average()
        val avg = avgRaw + avgRaw * getAvgMultiplier(serviceType)

        val neutralSpan = getNeutralSpan(serviceType)
        val upperBound = avg + (neutralSpan / 2)
        val lowerBound = avg - (neutralSpan / 2)

        return priceRange.map {
            CategorizedPrice(
                when {
                    it.price > upperBound -> PriceCategory.expensive
                    it.price <= lowerBound -> PriceCategory.cheap
                    else -> PriceCategory.neutral
                },
                it
            )
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
    val neutralSpan: Double,
    val avgMultiplier: Double,
    val categorizedPrices: List<CategorizedPrice>,
    val priceDecision: PriceDecision?,
)

enum class PriceCategory {
    cheap,
    neutral,
    expensive
}

data class CategorizedPrice(
    val category: PriceCategory,
    val price: Price,
)

fun List<CategorizedPrice>.priceDecision(): PriceDecision? {
    val now = Instant.now()
    if (isEmpty()) return null
    val i = this.indexOfFirst {
        it.price.isValidFor(now)
    }
    if (i < 0) return null
    val categorizedPrice = this[i]
    val future = this.subList(i, this.size)
    val changesAtIndex = future.indexOfFirst { it.category != categorizedPrice.category }
    val changesAt = if (changesAtIndex < 0) {
        this.last().price.to
    } else {
        future[changesAtIndex].price.from
    }
    val changesInto = if (changesAtIndex < 0) {
        PriceCategory.neutral
    } else {
        future[changesAtIndex].category
    }
    return PriceDecision(
        categorizedPrice.category,
        changesAt,
        changesInto
    )
}

data class PriceDecision(
    val current: PriceCategory,
    val changesAt: Instant,
    val changesInto: PriceCategory
)

