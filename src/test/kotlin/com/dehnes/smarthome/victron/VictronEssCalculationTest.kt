package com.dehnes.smarthome.victron

import com.dehnes.smarthome.victron.VictronEssCalculation.VictronEssCalculationInput
import com.dehnes.smarthome.victron.VictronEssCalculation.VictronEssCalculationResult
import com.dehnes.smarthome.victron.VictronEssCalculation.calculateAcPowerSetPoints
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class VictronEssCalculationTest {

    @Test
    fun testCompensate1() {
        assertEquals(
            VictronEssCalculationResult(1000, -500, -500), calculateAcPowerSetPoints(
                VictronEssCalculationInput(
                    6000, // over inverting limit
                    2000,
                    2000,
                    0,
                    -1,
                    -1,
                )
            )
        )
    }

    @Test
    fun testAllFromBatter() {
        assertEquals(
            VictronEssCalculationResult(0, 0, 0), calculateAcPowerSetPoints(
                VictronEssCalculationInput(
                    2000,
                    2000,
                    2000,
                    0,
                    -1,
                    -1,
                )
            )
        )
    }

    @Test
    fun test2() {
        assertEquals(
            VictronEssCalculationResult(1333, 1333, 1333), calculateAcPowerSetPoints(
                VictronEssCalculationInput(
                    6000,
                    2000,
                    2000,
                    0,
                    -1,
                    6000,
                )
            )
        )
    }

    @Test
    fun test3() {
        assertEquals(
            VictronEssCalculationResult(7000, 7000, 7000), calculateAcPowerSetPoints(
                VictronEssCalculationInput(
                    5000,
                    5000,
                    5000,
                    21000,
                    -1,
                    -1,
                )
            )
        )
    }
}
