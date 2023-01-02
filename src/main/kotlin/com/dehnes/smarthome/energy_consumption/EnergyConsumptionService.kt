package com.dehnes.smarthome.energy_consumption

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.objectMapper
import java.time.Instant

data class PowerPeriodeStart(
    val start: Instant,
    val powerInWatt: Double,
    val consumerId: String,
)

class EnergyConsumptionService(
    private val influxDBClient: InfluxDBClient
) {

    private val consumers = mutableMapOf<String, PowerPeriodeStart>()

    fun reportPower(
        consumerId: String,
        powerInWatt: Double,
    ) {
        val now = Instant.now()

        var influxDBRecord: InfluxDBRecord? = null

        synchronized(this) {
            val existing = consumers[consumerId]
            if (existing != null) {
                val durationInMs = (now.toEpochMilli() - existing.start.toEpochMilli()).toDouble()
                val durationInHours = durationInMs / (1000 * 3600)
                val watthours = existing.powerInWatt * durationInHours
                influxDBRecord = InfluxDBRecord(
                    now,
                    "energyConsumptions",
                    mapOf(
                        "consumedInWattHours" to watthours
                    ),
                    mapOf(
                        "consumerId" to consumerId
                    )
                )
            }
            consumers[consumerId] = PowerPeriodeStart(
                now,
                powerInWatt,
                consumerId
            )
        }

        influxDBRecord?.let {
            influxDBClient.recordSensorData(it)
        }
    }

    fun report(query: EnergyConsumptionQuery): EnergyConsumptionData {

        val data = influxDBClient.query(
            """
            from(bucket: "sensor_data")
                |> range(start: ${query.start}, stop: ${query.stop})
                |> filter(fn: (r) => r["_measurement"] == "energyConsumptions")
                |> filter(fn: (r) => r["_field"] == "consumedInWattHours")
                |> sum()
        """.trimIndent()
        )

        fun getValueFor(consumerId: String): Double {
            return data.firstOrNull { it.tags["consumerId"] == consumerId }?.value ?: 0.0
        }

        val houseKnownConsumers = data
            .filterNot { it.tags["consumerId"]!! in listOf("Grid", "HomeBattery", "HouseTotal") }
            .associate { it.tags["consumerId"]!! to it.value }

        val houseTotal = getValueFor("HouseTotal")
        val rest = houseTotal - houseKnownConsumers.values.sum()

        return EnergyConsumptionData(
            getValueFor("Grid"),
            getValueFor("HomeBattery"),
            houseTotal,
            houseKnownConsumers + ("Other" to rest),
        )
    }

}

data class EnergyConsumptionQuery(
    val start: Instant,
    val stop: Instant,
)

data class EnergyConsumptionData(
    val grid: Double,
    val battery: Double,
    val houseTotal: Double,
    val houseKnownConsumers: Map<String, Double>
)


fun main() {
    val objectMapper = objectMapper()
    val influxDBClient = InfluxDBClient(ConfigService(objectMapper))
    val energyConsumptionService = EnergyConsumptionService(influxDBClient)
    val report = energyConsumptionService.report(
        EnergyConsumptionQuery(
            Instant.parse("2019-08-28T22:00:00Z"),
            Instant.parse("2023-08-28T22:00:00Z"),
        )
    )

    println(report)
}
