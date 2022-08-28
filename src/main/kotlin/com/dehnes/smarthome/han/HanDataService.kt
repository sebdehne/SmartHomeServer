package com.dehnes.smarthome.han

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.energy_pricing.PowerDistributionPrices
import com.dehnes.smarthome.energy_pricing.tibber.TibberService
import com.dehnes.smarthome.utils.DateTimeUtils.roundToNearestFullHour
import mu.KotlinLogging
import java.time.Instant

class HanDataService(
    private val influxDBClient: InfluxDBClient,
    private val tibberService: TibberService,
) {

    private var previousTotalEnergyImport: Pair<Instant, Long>? = null
    private var previousTotalEnergyExport: Pair<Instant, Long>? = null

    private val logger = KotlinLogging.logger { }

    fun onNewData(hanData: HanData) {
        val data = hanDataToInfluxDb(hanData)

        val newRecord = InfluxDBRecord(
            hanData.createdAt,
            "electricityData",
            data.toMap(),
            mapOf("sensor" to "MainMeter")
        )

        influxDBClient.recordSensorData(newRecord)
    }

    fun hanDataToInfluxDb(hanData: HanData): List<Pair<String, Any>> {

        val totalEnergyImport = hanData.totalEnergyImport?.let { it * 10 }
        val totalEnergyExport = hanData.totalEnergyExport?.let { it * 10 }

        val influxDbData: MutableList<Pair<String, Any>> = listOfNotNull(
            "totalPowerImport" to hanData.totalPowerImport, // in Watt
            "totalPowerExport" to hanData.totalPowerExport, // in Watt
            "totalReactivePowerImport" to hanData.totalReactivePowerImport,
            "totalReactivePowerExport" to hanData.totalReactivePowerExport,
            "currentL1" to hanData.currentL1 * 10, // in milliAmpere
            "currentL2" to hanData.currentL2 * 10, // in milliAmpere
            "currentL3" to hanData.currentL3 * 10, // in milliAmpere
            "voltageL1" to hanData.voltageL1, // in Volt
            "voltageL2" to hanData.voltageL2, // in Volt
            "voltageL3" to hanData.voltageL3, // in Volt
            totalEnergyImport?.let { "totalEnergyImport" to it }, // Wh
            totalEnergyExport?.let { "totalEnergyExport" to it }, // Wh
            hanData.totalReactiveEnergyImport?.let { "totalReactiveEnergyImport" to it * 10 }, // Wh
            hanData.totalReactiveEnergyExport?.let { "totalReactiveEnergyExport" to it * 10 }, // Wh
        ).toMutableList()

        synchronized(this) {
            if (previousTotalEnergyImport == null) {
                previousTotalEnergyImport = getLatestValueFor(
                    "electricityData", "totalEnergyImport", mapOf(
                        "sensor" to "MainMeter"
                    )
                )?.apply {
                    logger.info { "Recovered previousTotalEnergyImport=$this " }
                }
            }
            if (previousTotalEnergyExport == null) {
                previousTotalEnergyExport = getLatestValueFor(
                    "electricityData", "totalEnergyExport", mapOf(
                        "sensor" to "MainMeter"
                    )
                )?.apply {
                    logger.info { "Recovered previousTotalEnergyExport=$this " }
                }
            }

            if (totalEnergyImport != null && previousTotalEnergyImport != null) {
                val endHour = hanData.createdAt.roundToNearestFullHour()
                val startHour = endHour.minusSeconds(60 * 60)

                if (previousTotalEnergyImport!!.first.roundToNearestFullHour() == startHour) {
                    val delta = totalEnergyImport - previousTotalEnergyImport!!.second
                    influxDbData.add("energyImportDeltaWh" to delta)

                    val prices = tibberService.getCachedPrices()

                    val priceInCents = prices.firstOrNull { it.isValidFor(startHour) }?.let { it.price * 100 }

                    if (priceInCents != null) {
                        val totalPriceInCents =
                            priceInCents + PowerDistributionPrices.getPowerDistributionPriceInCents(startHour)
                        val totalCostInCents = (delta.toDouble() / 1000) * totalPriceInCents
                        influxDbData.add("energyImportCostCents" to totalCostInCents.toLong())
                    }
                }
            }

            if (hanData.totalEnergyExport != null && previousTotalEnergyExport != null) {
                val endHour = hanData.createdAt.roundToNearestFullHour()
                val startHour = endHour.minusSeconds(60 * 60)

                if (previousTotalEnergyExport!!.first.roundToNearestFullHour() == startHour) {
                    val delta = hanData.totalEnergyExport - previousTotalEnergyExport!!.second
                    influxDbData.add("energyExportDeltaWh" to delta)

                    val prices = tibberService.getCachedPrices()

                    val priceInCents = prices.firstOrNull { it.isValidFor(startHour) }?.let { it.price * 100 }

                    if (priceInCents != null) {
                        val totalPriceInCents = priceInCents
                        val totalCostInCents = (delta.toDouble() / 1000) * totalPriceInCents * -1
                        influxDbData.add("energyExportCostCents" to totalCostInCents.toLong())
                    }
                }
            }

            totalEnergyImport?.let {
                previousTotalEnergyImport = hanData.createdAt to it
            }
            hanData.totalEnergyExport?.let {
                previousTotalEnergyExport = hanData.createdAt to it
            }
        }

        return influxDbData
    }

    fun getLatestValueFor(measurement: String, fieldName: String, tags: Map<String, String>): Pair<Instant, Long>? {
        val tagQueries = tags.entries.fold("") { acc, entry ->
            acc + "|> filter(fn: (r) => r[\"${entry.key}\"] == \"${entry.value}\")\r\n"
        }

        val data = influxDBClient.query(
            """
            from(bucket: "sensor_data")
                |> range(start: -1h, stop: now())
                |> filter(fn: (r) => r["_measurement"] == "$measurement")
                |> filter(fn: (r) => r["_field"] == "$fieldName")
                $tagQueries
        """.trimIndent()
        )

        return data.lastOrNull()?.let {
            it.time to it.value
        }
    }
}