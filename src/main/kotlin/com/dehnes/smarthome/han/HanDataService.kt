package com.dehnes.smarthome.han

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.energy_pricing.PowerDistributionPrices
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.utils.DateTimeUtils.roundToNearestFullHour
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant

class HanDataService(
    private val influxDBClient: InfluxDBClient,
    private val energyPriceService: EnergyPriceService,
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

    private fun hanDataToInfluxDb(hanData: HanData): List<Pair<String, Any>> {

        val influxDbData: MutableList<Pair<String, Any>> = listOfNotNull(
            "totalPowerImport" to hanData.totalPowerImport, // in Watt
            "totalPowerExport" to hanData.totalPowerExport, // in Watt
            "totalReactivePowerImport" to hanData.totalReactivePowerImport,
            "totalReactivePowerExport" to hanData.totalReactivePowerExport,
            "currentL1" to hanData.currentL1, // in milliAmpere
            "currentL2" to hanData.currentL2, // in milliAmpere
            "currentL3" to hanData.currentL3, // in milliAmpere
            "voltageL1" to hanData.voltageL1, // in Volt
            "voltageL2" to hanData.voltageL2, // in Volt
            "voltageL3" to hanData.voltageL3, // in Volt
            hanData.totalEnergyImport?.let { "totalEnergyImport" to it }, // Wh
            hanData.totalEnergyExport?.let { "totalEnergyExport" to it }, // Wh
            hanData.totalReactiveEnergyImport?.let { "totalReactiveEnergyImport" to it }, // Wh
            hanData.totalReactiveEnergyExport?.let { "totalReactiveEnergyExport" to it }, // Wh
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

            if (hanData.totalEnergyImport != null && previousTotalEnergyImport != null) {
                val endHour = hanData.createdAt.roundToNearestFullHour()
                val startHour = endHour.minusSeconds(60 * 60)

                if (previousTotalEnergyImport!!.first.roundToNearestFullHour() == startHour) {
                    val deltaInWh = hanData.totalEnergyImport - previousTotalEnergyImport!!.second
                    influxDbData.add("energyImportDeltaWh" to deltaInWh)

                    val prices = energyPriceService.getCachedPrices()

                    val energyPriceInCents = prices.firstOrNull { it.isValidFor(startHour) }?.let { it.price * 100 }

                    if (energyPriceInCents != null) {
                        val priceInCents = energyPriceInCents +
                                PowerDistributionPrices.getPowerDistributionPriceInCents(startHour)
                        val costInCents = (deltaInWh.toDouble() / 1000) * priceInCents
                        val costInNOK = costInCents / 100
                        influxDbData.add("energyImportCostLastHour" to costInNOK)
                    }
                }
            }

            if (hanData.totalEnergyExport != null && previousTotalEnergyExport != null) {
                val endHour = hanData.createdAt.roundToNearestFullHour()
                val startHour = endHour.minusSeconds(60 * 60)

                if (previousTotalEnergyExport!!.first.roundToNearestFullHour() == startHour) {
                    val deltaInWh = hanData.totalEnergyExport - previousTotalEnergyExport!!.second
                    influxDbData.add("energyExportDeltaWh" to deltaInWh)

                    val prices = energyPriceService.getCachedPrices()

                    val priceInCents = prices.firstOrNull { it.isValidFor(startHour) }?.let { it.price * 100 }

                    if (priceInCents != null) {
                        val costInCents = (deltaInWh.toDouble() / 1000) * priceInCents * -1
                        val costInNOK = costInCents / 100
                        influxDbData.add("energyExportCostLastHour" to costInNOK)
                    }
                }
            }

            hanData.totalEnergyImport?.let {
                previousTotalEnergyImport = hanData.createdAt to it
            }
            hanData.totalEnergyExport?.let {
                previousTotalEnergyExport = hanData.createdAt to it
            }
        }

        return influxDbData
    }

    private fun getLatestValueFor(measurement: String, fieldName: String, tags: Map<String, String>): Pair<Instant, Long>? {
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
            it.time!! to it.value.toLong()
        }
    }
}