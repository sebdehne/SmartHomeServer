package com.dehnes.smarthome.energy_pricing

import com.dehnes.smarthome.datalogging.InfluxDBRecord
import java.time.Instant

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