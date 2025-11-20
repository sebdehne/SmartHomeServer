package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.energy_pricing.PowerDistributionPrices.tax
import com.dehnes.smarthome.objectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.hc.client5.http.fluent.Request
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HvakosterstrommenClient(
    private val objectMapper: ObjectMapper,
) : PriceSource {

    private val logger = KotlinLogging.logger { }

    override fun getPrices(): List<Price> {
        val now = LocalDate.now()
        return listOf(
            now.minusDays(1),
            now,
            now.plusDays(1)
        ).flatMap { day ->
            logger.info { "Fetching prices for $day ..." }
            val prices = getDay(day)
            logger.info { "Fetching prices for $day ... Done: $prices" }
            prices
        }
    }

    fun getDay(day: LocalDate): List<Price> = try {
        val url =
            "https://www.hvakosterstrommen.no/api/v1/prices/${day.format(DateTimeFormatter.ofPattern("yyyy/MM-dd"))}_NO1.json"
        logger.info { "URl=$url" }
        val response =
            Request.get(url)
                .useExpectContinue()
                .execute()
                .returnContent().asString()

        val prices = objectMapper.readValue<List<Map<String, Any>>>(response)
        prices.map { price ->
            val any = price["NOK_per_kWh"].let {
                when (it) {
                    is Int -> it.toDouble()
                    is Double -> it
                    else -> error("type $it not supported")
                }
            }
            Price(
                Instant.parse(price["time_start"] as String),
                Instant.parse(price["time_end"] as String),
                any * tax,
            )
        }
    } catch (e: Exception) {
        logger.warn(e) { "Could not fetch prices" }
        emptyList()
    }
}

fun main() {
    val objectMapper = objectMapper()
    val client = HvakosterstrommenClient(objectMapper)
    val f = File("prices.csv")
    f.outputStream().writer(Charsets.UTF_8).use { os ->
        os.write("\ufeff")

        for (d in (1..31)) {
            val prices = client.getDay(LocalDate.of(2022, 12, d))
            prices.forEach { p ->
                val line = listOf(
                    p.from.toString(),
                    p.price.toString()
                ).joinToString(";") + "\r\n"
                os.write(line)
                println(line)
            }
        }

        os.flush()
    }

}