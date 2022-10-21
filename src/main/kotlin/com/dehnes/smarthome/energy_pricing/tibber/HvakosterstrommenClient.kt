package com.dehnes.smarthome.energy_pricing.tibber

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.http.HttpVersion
import org.apache.http.client.fluent.Request
import java.time.Instant
import java.time.LocalDate

class HvakosterstrommenClient(
    private val objectMapper: ObjectMapper,
) : PriceSource {

    private val logger = KotlinLogging.logger { }

    override fun getPrices() = try {
        val now = LocalDate.now()
        listOf(
            now.minusDays(1),
            now,
            now.plusDays(1)
        ).flatMap { day ->
            logger.info { "Fetching prices for $day ..." }
            val prices = getDay(day)
            logger.info { "Fetching prices for $day ... Done: $prices" }
            prices
        }
    } catch (e: Exception) {
        logger.warn(e) { "Failed to fetch prices" }
        null
    }

    private fun getDay(day: LocalDate): List<Price> {
        val url = "https://www.hvakosterstrommen.no/api/v1/prices/${day.year}/${day.monthValue}-${day.dayOfMonth}_NO1.json"
        logger.info { "URl=$url" }
        val response =
            Request.Get(url)
                .useExpectContinue()
                .version(HttpVersion.HTTP_1_1)
                .execute()
                .returnContent().asString()

        val prices = objectMapper.readValue<List<Map<String, Any>>>(response)
        return prices.map { price ->
            Price(
                Instant.parse(price["time_start"] as String),
                Instant.parse(price["time_end"] as String),
                (price["NOK_per_kWh"] as Double) * 1.25,
            )
        }

    }
}