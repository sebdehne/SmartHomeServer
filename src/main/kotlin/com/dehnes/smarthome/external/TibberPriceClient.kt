package com.dehnes.smarthome.external

import com.dehnes.smarthome.service.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.http.HttpVersion
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TibberPriceClient(
    private val objectMapper: ObjectMapper,
    private val persistenceService: PersistenceService
) {

    private val logger = KotlinLogging.logger { }

    fun getPrices() = try {
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
                "Bearer " + persistenceService.get("tibberAuthBearer", "authkeyMangler")
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
            val startAt = Instant.parse(current["startsAt"] as CharSequence?)
            var endsAt = startAt.plus(30, ChronoUnit.DAYS)
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
    fun isValidFor(input: Instant) = (input.isAfter(from) || input == from) && input.isBefore(to)

    fun isValidForDay(input: LocalDate): Boolean {
        val fromDay = from.atZone(ZoneId.systemDefault()).toLocalDate()
        val toDay = to.atZone(ZoneId.systemDefault()).toLocalDate()
        return input == fromDay || input == toDay
    }
}