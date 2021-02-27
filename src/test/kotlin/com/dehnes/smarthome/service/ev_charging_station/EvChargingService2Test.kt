package com.dehnes.smarthome.service.ev_charging_station

import com.dehnes.smarthome.api.dtos.ProximityPilotAmps
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class EvChargingService2Test {

    @Test
    fun testPwmCalc() {
        (0..32).forEach { rate ->
            val pwmPercent = chargingRateToPwmPercent(rate, ProximityPilotAmps.Amp32)
            check(isSame(pwmPercent, rate))
        }

        assertEquals(100, chargingRateToPwmPercent(0, ProximityPilotAmps.Amp32))
    }
}