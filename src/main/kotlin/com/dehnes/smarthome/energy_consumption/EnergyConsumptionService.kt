package com.dehnes.smarthome.energy_consumption

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
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

}

