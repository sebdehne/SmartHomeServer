package com.dehnes.smarthome.datalogging

import com.dehnes.smarthome.api.dtos.QuickStatsResponse
import com.dehnes.smarthome.han.HanPortService
import com.dehnes.smarthome.victron.SystemState
import com.dehnes.smarthome.victron.VictronService
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class QuickStatsService(
    private val influxDBClient: InfluxDBClient,
    hanPortService: HanPortService,
    private val executorService: ExecutorService,
    private val victronService: VictronService,
) {

    val listeners = ConcurrentHashMap<String, (QuickStatsResponse) -> Unit>()
    private val logger = KotlinLogging.logger { }

    init {
        hanPortService.listeners.add { hanData ->
            refetch()
            notifyListeners()
        }
        victronService.listeners["QuickStatsService"] = {
            refetch()
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        executorService.submit {
            listeners.forEach { (t, u) ->
                try {
                    u(getStats())
                } catch (e: Exception) {
                    logger.error("t=$t", e)
                }
            }
        }
    }


    var quickStatsResponse: QuickStatsResponse = QuickStatsResponse(
        0,
        0,
        0.0,
        0.0,
        0,
        0.0,
        0.0,
        SystemState.Off,
        0,
        0
    )

    @Volatile
    var createdAt: Instant = Instant.MIN
    val timeout = Duration.ofSeconds(5)

    fun getStats(): QuickStatsResponse {
        if (Instant.now().isAfter(createdAt + timeout)) {
            refetch()
        }
        return quickStatsResponse
    }

    private fun refetch() {
        val essValues = victronService.current()
        val quickStatsResponse1 = QuickStatsResponse(
            getPowerImport().toLong(),
            getPowerExport().toLong(),
            getCostEnergyImportedToday(),
            getCostEnergyImportedCurrentMonth(),
            energyUsedToday().toLong(),
            getOutsideTemperature(),
            currentEnergyPrice(),
            essValues.systemState,
            essValues.batteryPower.toLong(),
            essValues.soc.toInt()
        )
        synchronized(this) {
            quickStatsResponse = quickStatsResponse1
            createdAt = Instant.now()
        }
    }

    private fun getPowerImport() = influxDBClient.query(
        """
        from(bucket:"sensor_data")
          |> range(start: -12h, stop: now())
          |> filter(fn: (r) => r["_measurement"] == "electricityData")
          |> filter(fn: (r) => r["_field"] == "totalPowerImport")
          |> filter(fn: (r) => r["sensor"] == "MainMeter")
          |> yield()
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

    private fun getPowerExport() = influxDBClient.query(
        """
        from(bucket:"sensor_data")
          |> range(start: -12h, stop: now())
          |> filter(fn: (r) => r["_measurement"] == "electricityData")
          |> filter(fn: (r) => r["_field"] == "totalPowerExport")
          |> filter(fn: (r) => r["sensor"] == "MainMeter")
          |> yield()
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

    private fun getCostEnergyImportedToday() = influxDBClient.query(
        """
        import "date"
        today = date.truncate(t: now(), unit: 1d)

        from(bucket:"sensor_data")
          |> range(start: today)
          |> filter(fn: (r) => r._measurement == "electricityData")
          |> filter(fn: (r) => r["sensor"] == "MainMeter")
          |> filter(fn: (r) => r._field == "energyImportCostLastHour")
          |> cumulativeSum()
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

    private fun getCostEnergyImportedCurrentMonth() = influxDBClient.query(
        """
        import "date"
        month = date.truncate(t: now(), unit: 1mo)

        from(bucket:"sensor_data")
          |> range(start: month)
          |> filter(fn: (r) => r._measurement == "electricityData")
          |> filter(fn: (r) => r["sensor"] == "MainMeter")
          |> filter(fn: (r) => r._field == "energyImportCostLastHour")
          |> cumulativeSum()
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

    private fun getOutsideTemperature() = influxDBClient.query(
        """
        from(bucket: "sensor_data")
          |> range(start: -3h, stop: now())
          |> filter(fn: (r) => r["_measurement"] == "sensor")
          |> filter(fn: (r) => r["_field"] == "temperature")
          |> filter(fn: (r) => r["room"] == "outside_combined")
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

    private fun energyUsedToday() = influxDBClient.query(
        """
        import "date"
        today = date.truncate(t: now(), unit: 1d)

        from(bucket:"sensor_data")
          |> range(start: today)
          |> filter(fn: (r) => r._measurement == "electricityData")
          |> filter(fn: (r) => r["sensor"] == "MainMeter")
          |> filter(fn: (r) => r._field == "totalEnergyImport")
          |> difference(keepFirst: false)
          |> cumulativeSum()
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

    private fun currentEnergyPrice() = influxDBClient.query(
        """
        from(bucket: "sensor_data")
            |> range(start: -12h, stop: now())
            |> filter(fn: (r) => r["_measurement"] == "energyPrice")
            |> filter(fn: (r) => r["_field"] == "price")
    """.trimIndent()
    ).lastOrNull()?.value ?: 0.0

}