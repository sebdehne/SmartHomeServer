package com.dehnes.smarthome.victron

import kotlin.math.absoluteValue

object VictronEssCalculation {

    data class VictronEssCalculationInput(
        val outputPowerL1: Long,
        val outputPowerL2: Long,
        val outputPowerL3: Long,
        val acPowerSetPoint: Long,
        val maxChargePower: Long,
        val maxDischargePower: Long,
    )

    data class VictronEssCalculationResult(
        val acPowerSetPointL1: Long,
        val acPowerSetPointL2: Long,
        val acPowerSetPointL3: Long,
    )

    fun calculateAcPowerSetPoints(input: VictronEssCalculationInput): VictronEssCalculationResult {
        // we must use ESS mode 3, because ESS mode 2 does not support setting a SoC upper/lower limit, and
        // we do not want to risk draining the battery if this app loses contact with the victron-system
        // In ESS mode 3, it automatically goes to pass through if no updates are received

        val outputPower = input.outputPowerL1 + input.outputPowerL2 + input.outputPowerL3

        val acTarget = if (input.acPowerSetPoint > outputPower) {
            // need to charge
            val charge = input.acPowerSetPoint - outputPower
            if (input.maxChargePower > -1 && charge > input.maxChargePower) {
                outputPower + input.maxChargePower
            } else {
                input.acPowerSetPoint
            }
        } else if (input.acPowerSetPoint < outputPower) {
            // need to discharge
            val discharge = outputPower - input.acPowerSetPoint
            if (input.maxDischargePower > -1 && discharge > input.maxDischargePower) {
                outputPower - input.maxDischargePower
            } else {
                input.acPowerSetPoint
            }
        } else {
            // passthrough
            input.acPowerSetPoint
        }

        val allPhases = listOf(
            Phase(
                1,
                input.outputPowerL1,
                acTarget / 3,
            ),
            Phase(
                2,
                input.outputPowerL2,
                acTarget / 3,
            ),
            Phase(
                3,
                input.outputPowerL3,
                acTarget / 3,
            ),
        )

        // optimization loop
        for (i in 1..10) {

            allPhases.forEach { phase ->
                val otherPhases = allPhases.filterNot { it.id == phase.id }

                val targetMissBy = acTarget - allPhases.sumOf { it.acPowerSetPoint }
                if (targetMissBy == 0L) return@forEach

                otherPhases.forEach { o ->
                    o.adjustAc(targetMissBy / 2)
                }
            }
        }

        return VictronEssCalculationResult(
            allPhases.first { it.id == 1 }.acPowerSetPoint,
            allPhases.first { it.id == 2 }.acPowerSetPoint,
            allPhases.first { it.id == 3 }.acPowerSetPoint,
        )
    }


    class Phase(
        val id: Int,
        val outputPower: Long,
        acTarget: Long,
    ) {

        var acPowerSetPoint: Long

        init {
            acPowerSetPoint = calcPhase(
                acTarget,
                outputPower
            )
        }

        fun adjustAc(value: Long) {
            acPowerSetPoint = calcPhase(
                acPowerSetPoint + value,
                outputPower
            )
        }
    }

    // Victron MultiPlus-II 48/5000/70
    val limitChargePower = (70 * 48).toLong()
    val limitInvertingPower = 5000L

    fun calcPhase(acTarget: Long, outputPower: Long): Long {
        val dcTarget = acTarget - outputPower

        return if (dcTarget < 0) {
            // discharging
            if (dcTarget.absoluteValue > limitInvertingPower) {
                // need compensation
                val c = dcTarget.absoluteValue - limitInvertingPower
                (acTarget + c)
            } else {
                // no compensation needed
                acTarget
            }
        } else if (dcTarget > 0) {
            // charging
            if (dcTarget > limitChargePower) {
                // need compensation
                val c = dcTarget - limitChargePower
                (acTarget - c)
            } else {
                // no compensation needed
                acTarget
            }
        } else {
            // passthrough - no compensation needed
            acTarget
        }
    }
}