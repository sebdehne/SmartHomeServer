package com.dehnes.smarthome.room_sensors

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.utils.merge
import com.dehnes.smarthome.rf433.RfPacket
import mu.KotlinLogging
import java.time.Instant
import kotlin.math.absoluteValue

class ChipCap2SensorService(
    private val influxDBClient: InfluxDBClient
) {
    companion object {
        fun calcTemperature(packet: RfPacket) =
            ((merge(packet.message[3], packet.message[2]) / 16384f * 165 - 40) * 100).toInt()

        fun calcRelativeHumidity(packet: RfPacket) =
            (merge(packet.message[1], packet.message[0]) / 16384f * 100 * 100).toInt()

        fun getAdcValue(packet: RfPacket, pos: Int) = merge(packet.message[pos + 1], packet.message[pos])

        fun calcVoltage(adcValue: Int) = ((102300 / adcValue) * 6) / 10
    }

    private val logger = KotlinLogging.logger { }

    private val sensorRepo = mapOf(
        2 to "bath",
        3 to "storage",
        4 to "out-west",
        5 to "out-east",
        6 to "test-sensor",
        7 to "tv_room",
        8 to "bath_kids", // was living_room
        9 to "hallway_down",
        10 to "sleeping_room",
        11 to "leilighet", // was kitchen, was under_floor
        12 to "mynthe_room",
        13 to "noan_room",
        14 to "garage",
    )

    private val previousValues = mutableMapOf<String, SensorData>()

    @Synchronized
    fun handleIncoming(p: RfPacket): Boolean {
        val sensorData = SensorData.fromRf(sensorRepo[p.remoteAddr] ?: return false, p)
        logger.info("Received sensorData=$sensorData")
        if (accept(sensorData)) {
            influxDBClient.recordSensorData(
                "sensor",
                sensorData.toInfluxDbFields(),
                "room" to sensorData.name
            )
        }
        return true
    }

    private fun accept(sensorData: SensorData): Boolean {
        val previous = previousValues[sensorData.name]
        return if (previous == null || previous.ageInSeconds() > 15 * 60) {
            previousValues[sensorData.name] = sensorData
            true
        } else {
            val delta = ((sensorData.tempValue - previous.tempValue).absoluteValue) / 100
            (delta <= 5).apply {
                if (!this) {
                    logger.info("Ignoring abnormal values. previous=$previous")
                }
            }
        }
    }
}

data class SensorData(
    val name: String,
    val tempValue: Int,
    val humidity: Int,
    val light: Int,
    val batteryVolt: Int,
    val counter: Int,
    val receivedAt: Instant = Instant.now()
) {
    companion object {
        fun fromRf(name: String, p: RfPacket): SensorData {
            val tempValue = ChipCap2SensorService.calcTemperature(p)
            val humidity = ChipCap2SensorService.calcRelativeHumidity(p)
            val light = ChipCap2SensorService.getAdcValue(p, 4)
            val batteryVolt = ChipCap2SensorService.calcVoltage(ChipCap2SensorService.getAdcValue(p, 6))
            val counter = p.message[8]

            return SensorData(
                name,
                tempValue, humidity, light, batteryVolt, counter
            )
        }
    }

    fun toTemperatur() = (tempValue.toFloat() / 100).toString()
    fun toHumidity() = (humidity.toFloat() / 100).toString()
    fun toLight() = light.toString()
    fun toBatteryVolt() = (batteryVolt.toFloat() / 100).toString()
    fun toCounter() = counter.toString()

    override fun toString(): String {
        return "SensorData(" +
                "tempValue=${toTemperatur()}, " +
                "humidity=${toHumidity()}, " +
                "light=${toLight()}, " +
                "batteryVolt=${toBatteryVolt()}, " +
                "counter=${toCounter()}, " +
                "receivedAt=$receivedAt)"
    }

    fun toInfluxDbFields() = listOf(
        "temperature" to toTemperatur(),
        "humidity" to toHumidity(),
        "counter" to toCounter(),
        "light" to toLight(),
        "battery_volt" to toBatteryVolt()
    )

    fun ageInSeconds() = (System.currentTimeMillis() - receivedAt.toEpochMilli()) / 1000


}