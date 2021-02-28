package com.dehnes.smarthome.utils

import com.dehnes.smarthome.rf433.RfPacket
import kotlin.math.pow

object Sht15Calculator {
    fun calculateTermperature(p: RfPacket) = calculateTemperature(merge(p.message[1], p.message[0]))

    fun calculateTemperature(`in`: Int) = ((`in`.toFloat() * 0.01 + -40.1f) * 100).toInt()

    fun calculateRelativeHumidity(rfPacket: RfPacket, temperature: Int) =
        calculateRelativeHumidity(merge(rfPacket.message[3], rfPacket.message[2]), temperature)

    fun calculateRelativeHumidity(SO_rh: Int, temperature: Int): Int {
        val temp = (temperature.toFloat() / 100f).toDouble()
        val c1 = -2.0468
        val c2 = 0.0367
        val c3 = -1.5955 / 1000000

        // calc rhLin
        val rhLin = c1 + c2 * SO_rh + c3 * SO_rh.toDouble().pow(2.0)
        val t1 = 0.01
        val t2 = 0.00008
        val rHtrue = (temp - 25) * (t1 + t2 * SO_rh) + rhLin
        return (rHtrue * 100f).toInt()
    }

}
