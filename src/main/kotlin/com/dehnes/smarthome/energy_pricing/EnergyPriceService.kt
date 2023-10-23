package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.EnergyPriceServiceSettings
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.DateTimeUtils
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
    private val configService: ConfigService,
    private val userSettingsService: UserSettingsService,
) : AbstractProcess(executorService, 60 * 5) {

    private val logger = KotlinLogging.logger { }
    private val backOffInMs = 60L * 60L * 1000L
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
    fun getCachedPrices(): List<Price> {
        ensureCacheLoaded()
        return priceCache
    }

    fun getAllSettings(user: String?): List<EnergyPriceConfig> {
        check(userSettingsService.canUserRead(user, UserRole.energyPricing)) {"User $user cannot read price settings"}
        return configService.getEnergyPriceServiceSettings().entries.map { (service, settings) ->
            val today = Instant.now().atZone(DateTimeUtils.zoneId).toLocalDate()
            val categorizedPrices = listOf(
                today.minusDays(1),
                today,
                today.plusDays(1),
            )
                .map { findSuitablePrices(user, service, it) }
                .flatten()
            EnergyPriceConfig(
                service,
                settings.neutralSpan,
                settings.avgMultiplier,
                categorizedPrices,
                categorizedPrices.priceDecision()
            )
        }
    }

    fun setNeutralSpan(user: String?, serviceType: String, neutralSpan: Double) {
        check(userSettingsService.canUserWrite(user, UserRole.energyPricing)) {"User $user cannot update price settings"}
        configService.setEnergiPriceServiceSetting(
            serviceType,
            getEnergyPriceServiceSetting(serviceType).copy(
                neutralSpan = neutralSpan
            )
        )
    }

    fun setAvgMultiplier(user: String?, serviceType: String, avgMultiplier: Double) {
        check(userSettingsService.canUserWrite(user, UserRole.energyPricing)) {"User $user cannot update price settings"}
        configService.setEnergiPriceServiceSetting(
            serviceType,
            getEnergyPriceServiceSetting(serviceType).copy(
                avgMultiplier = avgMultiplier
            )
        )
    }

    @Synchronized
    fun findSuitablePrices(user: String?, serviceType: String, day: LocalDate): List<CategorizedPrice> {
        check(userSettingsService.canUserRead(user, UserRole.energyPricing)) {"User $user cannot read price settings"}
        ensureCacheLoaded()
        val settings = getEnergyPriceServiceSetting(serviceType)

        val periodFrom = day.atStartOfDay(DateTimeUtils.zoneId).toInstant()
        val periodUntil = (day.plusDays(1)).atStartOfDay(DateTimeUtils.zoneId).toInstant()

        val priceRange = priceCache
            .sortedBy { it.from }
            .filter { it.from >= periodFrom }
            .filter { it.to <= periodUntil }

        if (priceRange.isEmpty()) return emptyList()

        val avgRaw = priceRange.map { it.price }.average()
        val avg = avgRaw + avgRaw * settings.avgMultiplier

        val neutralSpan = settings.neutralSpan
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

    private fun getEnergyPriceServiceSetting(serviceType: String) =
        configService.getEnergyPriceServiceSettings()[serviceType] ?: run {
            val default = EnergyPriceServiceSettings(0.0, 0.8)
            configService.setEnergiPriceServiceSetting(serviceType, default)
            default
        }

    private fun ensureCacheLoaded() {
        if (lastReload + backOffInMs < System.currentTimeMillis()) {
            reloadCacheNow()
        }
    }

    private fun transformPrices(original: List<Price>): List<Price> = original.map {
        val price = it.price
        // https://www.regjeringen.no/no/tema/energi/regjeringens-stromtiltak/id2900232/?expand=factbox2900261
        val threshold = 0.7 * 1.25

        val finalPrice = if (price > threshold) {
            val topPart = price - threshold
            val tenPercent = topPart * 0.1
            threshold + tenPercent
        } else price

        it.copy(
            price = finalPrice
        )
    }

    private fun reloadCacheNow() {
        logger.info("Fetching energy prices...")
        val prices = priceSource.getPrices()
        lastReload = System.currentTimeMillis()
        if (!prices.isNullOrEmpty()) {
            priceCache = transformPrices(prices)
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

