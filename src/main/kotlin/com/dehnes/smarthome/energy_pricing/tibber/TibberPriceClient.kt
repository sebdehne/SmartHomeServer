package com.dehnes.smarthome.energy_pricing.tibber

import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.http.HttpVersion
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import java.time.Instant
import java.time.temporal.ChronoUnit

class TibberPriceClient(
    private val objectMapper: ObjectMapper,
    private val persistenceService: PersistenceService
): PriceSource {

    private val logger = KotlinLogging.logger { }

    override fun getPrices() = try {
        val query = """
            {
              "query": "{\n  viewer {\n    homes {\n      currentSubscription {\n        priceInfo {\n          today {\n            total\n            energy\n            tax\n            startsAt\n          }\n          tomorrow {\n            total\n            energy\n            tax\n            startsAt\n          }\n        }\n      }\n    }\n  }\n}\n",
              "variables": null,
              "operationName": null
            }
        """.trimIndent()

        val graphQLResponse = Request.Post("https://api.tibber.com/v1-beta/gql")
            .useExpectContinue()
            .version(HttpVersion.HTTP_1_1)
            .addHeader(
                "Authorization",
                "Bearer " + persistenceService["tibberAuthBearer", "authkeyMangler"]
            )
            .bodyString(query, ContentType.APPLICATION_JSON)
            .execute()
            .returnContent().asString()

        val jsonRaw: Map<String, Any> = objectMapper.readValue(graphQLResponse)
        val data = jsonRaw["data"] as Map<String, Any>
        val viewer = data["viewer"] as Map<String, Any>
        val homes = viewer["homes"] as List<*>
        val home = homes[0] as Map<String, Any>
        val currentSubscription = home["currentSubscription"] as Map<String, Any>
        val priceInfo = currentSubscription["priceInfo"] as Map<String, Any>
        val prices = mutableListOf<Map<String, Any>>()
        prices.addAll(priceInfo["today"] as List<Map<String, Any>>)
        prices.addAll(priceInfo["tomorrow"] as List<Map<String, Any>>)

        val result = mutableListOf<Price>()

        prices.forEachIndexed { i, current ->
            val startAt = Instant.parse(current["startsAt"] as CharSequence)
            var endsAt = startAt.plus(1, ChronoUnit.HOURS)
            if (i + 1 < prices.size) {
                endsAt = Instant.parse(prices[i + 1]["startsAt"] as CharSequence?)
            }
            result.add(
                Price(
                    startAt,
                    endsAt!!,
                    (current["total"] as Double?)!!
                )
            )
        }

        result.toList()
    } catch (e: Exception) {
        logger.warn("", e)
        null
    }
}

data class Price(
    var from: Instant,
    var to: Instant,
    var price: Double
) {
    fun isValidFor(input: Instant) = input.toEpochMilli() in (from.toEpochMilli() until to.toEpochMilli())

    fun toInfluxDbRecords(): List<InfluxDBRecord> {
        var current = from
        val result = mutableListOf<InfluxDBRecord>()
        while (current.isBefore(to)) {
            result.add(
                InfluxDBRecord(
                    current,
                    "energyPrice",
                    mapOf(
                        "price" to price.toString()
                    ),
                    mapOf(
                        "service" to "Tibber"
                    )
                )
            )

            current = current.plusSeconds(60)
        }
        return result
    }
}