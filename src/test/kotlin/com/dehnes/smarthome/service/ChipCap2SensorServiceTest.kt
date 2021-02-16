package com.dehnes.smarthome.service

import com.dehnes.smarthome.external.RfPacket
import com.dehnes.smarthome.service.ChipCap2SensorService.Companion.calcVoltage
import com.dehnes.smarthome.service.ChipCap2SensorService.Companion.getAdcValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ChipCap2SensorServiceTest {

    @Test
    fun test() {
        val packet = RfPacket(13, intArrayOf(17, 132, 22, 125, 2, 125, 0, 145, 87))
        val temperature = ChipCap2SensorService.calcTemperature(packet)
        val input = calcVoltage(getAdcValue(packet, 6))

        assertEquals(1797, temperature)
        assertEquals(423, input)
    }
}