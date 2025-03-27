package com.dehnes.smarthome.daylight

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.withLogging
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.hc.client5.http.fluent.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class DayLightService(
    private val configService: ConfigService,
    private val objectMapper: ObjectMapper,
    executorService: ExecutorService
) : AbstractProcess(executorService, 5) {
    private val logger = KotlinLogging.logger { }

    @Volatile
    private var dayLightState: DayLightState? = null

    @Volatile
    private var lastBroadcasted: DayLightSunState? = null

    val listeners = ConcurrentHashMap<String, (s: DayLightSunState) -> Unit>()

    override fun logger() = logger

    override fun tickLocked(): Boolean {
        ensureFetched()

        val currentStatus = getStatus()
        if (lastBroadcasted != currentStatus) {
            lastBroadcasted = currentStatus
            executorService.submit(withLogging {
                listeners.forEach { (_, fn) ->
                    fn(lastBroadcasted!!)
                }
            })
        }

        return true
    }

    fun getStatus(now: Instant = Instant.now()): DayLightSunState? {
        ensureFetched()
        return dayLightState?.let {
            if (now in it.sunrise..it.sunset) DayLightSunState.up else DayLightSunState.down
        }
    }

    private fun ensureFetched() {
        synchronized(this) {
            if (dayLightState?.isValid() != true) {
                logger.info { "Need to refetch day light data" }
                val coordinates = configService.getCoordinates() ?: run {
                    logger.warn { "coordinates not configured - cannot determine daylight settings." }
                    return
                }
                val url =
                    "https://api.met.no/weatherapi/sunrise/3.0/sun?lat=${coordinates.latitude.trim()}&lon=${coordinates.longitude.trim()}&date=${LocalDate.now()}"
                logger.info { "URl=$url" }
                val response =
                    Request.get(url)
                        .addHeader("User-Agent", "sebastian@dehnes.com-${UUID.randomUUID().toString()}")
                        .useExpectContinue()
                        .execute()
                        .returnContent()
                        .asString()

                val readValue = objectMapper.readValue<Map<String, Any>>(response)
                val whn = readValue["when"] as Map<String, Any>
                val properties = readValue["properties"] as Map<String, Any>
                val interval = whn["interval"] as List<String>
                val sunrise = (properties["sunrise"] as Map<String, Any>)["time"] as String
                val sunset = (properties["sunset"] as Map<String, Any>)["time"] as String

                dayLightState = DayLightState(
                    from = ZonedDateTime.parse(interval[0]).toInstant(),
                    to = ZonedDateTime.parse(interval[1]).toInstant(),
                    sunrise = ZonedDateTime.parse(sunrise).toInstant(),
                    sunset = ZonedDateTime.parse(sunset).toInstant(),
                )
            }
        }
    }
}

data class DayLightState(
    val from: Instant,
    val to: Instant,
    val sunrise: Instant,
    val sunset: Instant,
)

fun DayLightState.isValid(now: Instant = Instant.now()): Boolean = now in from..to

enum class DayLightSunState {
    up,
    down
}

