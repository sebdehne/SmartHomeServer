package com.dehnes.smarthome.service

import com.dehnes.smarthome.external.InfluxDBClient
import com.dehnes.smarthome.external.RfPacket
import com.dehnes.smarthome.math.divideBy100
import com.dehnes.smarthome.math.merge
import mu.KotlinLogging

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

    fun handleIncoming(p: RfPacket): Boolean {
        val name = sensorRepo[p.remoteAddr] ?: return false
        val tempValue = calcTemperature(p)
        val temp: String = divideBy100(tempValue)
        val humidity: String = divideBy100(calcRelativeHumidity(p))
        val light = getAdcValue(p, 4).toString()
        val batteryVolt: String = divideBy100(calcVoltage(getAdcValue(p, 6)))
        val counter = p.message[8].toString()
        logger.info("Relative humidity $humidity")
        logger.info("Temperature $temp")
        logger.info("Light $light")
        logger.info("Counter $counter")
        logger.info("Battery $batteryVolt")
        if (tempValue > -4000 && tempValue < 8000) {
            // TODO better detection for outOfRange values
            // record received data in db
            influxDBClient.recordSensorData(
                name,
                temp,
                humidity,
                counter,
                light,
                batteryVolt
            )
        } else {
            logger.info("Ignoring abnormal values")
        }
        return true
    }

}