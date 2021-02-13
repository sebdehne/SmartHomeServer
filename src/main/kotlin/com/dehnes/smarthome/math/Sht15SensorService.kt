package com.dehnes.smarthome.math

import com.dehnes.smarthome.external.RfPacket

object Sht15SensorService {
    fun getTemperature(p: RfPacket) = calcTemp(merge(p.message[1], p.message[0]))

    fun calcTemp(`in`: Int) = ((`in`.toFloat() * 0.01 + -40.1f) * 100).toInt()

    fun getRelativeHumidity(rfPacket: RfPacket, temperature: Int) =
        calcHum(merge(rfPacket.message[3], rfPacket.message[2]), temperature)

    fun calcHum(SO_rh: Int, temperature: Int): Int {
        val temp = (temperature.toFloat() / 100f).toDouble()
        val c1 = -2.0468
        val c2 = 0.0367
        val c3 = -1.5955 / 1000000

        // calc rhLin
        val rhLin = c1 + c2 * SO_rh + c3 * Math.pow(SO_rh.toDouble(), 2.0)
        val t1 = 0.01
        val t2 = 0.00008
        val rHtrue = (temp - 25) * (t1 + t2 * SO_rh) + rhLin
        return (rHtrue * 100f).toInt()
    }

}

fun main(args: Array<String>) {
    val temp = Sht15SensorService.calcTemp(merge(120, 24))
    println(temp)
    println(Sht15SensorService.calcHum(merge(160.toByte(), 4), temp))
}
