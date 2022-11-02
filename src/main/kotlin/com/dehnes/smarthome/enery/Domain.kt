package com.dehnes.smarthome.enery

import java.time.Instant

typealias Milliwatts = Long
typealias WattHours = Long
typealias Periode = ClosedRange<Instant>

interface EnergyUnit {
    val id: String
    val name: String

    // positive: providing power
    // negative: consuming power
    fun currentPowerL1(): Milliwatts
    fun currentPowerL2(): Milliwatts
    fun currentPowerL3(): Milliwatts
}


class EnergyService {

    private val energyUnits = mutableMapOf<String, EnergyUnit>()

    fun addUnit(energyUnit: EnergyUnit) {
        synchronized(energyUnits) {
            energyUnits[energyUnit.id] = energyUnit
        }
    }

    fun removeUnit(id: String) {
        synchronized(energyUnits) {
            energyUnits.remove(id)
        }
    }

    fun getAll(): List<EnergyUnit> {
        val result = mutableListOf<EnergyUnit>()

        var otherPowerL1: Milliwatts = 0
        var otherPowerL2: Milliwatts = 0
        var otherPowerL3: Milliwatts = 0

        synchronized(energyUnits) {
            energyUnits.forEach { (_, energyUnit) ->
                otherPowerL1 -= energyUnit.currentPowerL1()
                otherPowerL2 -= energyUnit.currentPowerL2()
                otherPowerL3 -= energyUnit.currentPowerL3()
                result.add(energyUnit)
            }
        }

        result.add(OtherLoads(otherPowerL1, otherPowerL2, otherPowerL3))

        return result
    }

}

class OtherLoads(
    private val powerL1: Milliwatts,
    private val powerL2: Milliwatts,
    private val powerL3: Milliwatts,
) : EnergyUnit {
    override val name: String
        get() = "Other loads"
    override val id: String
        get() = "OtherLoads"

    override fun currentPowerL1() = powerL1
    override fun currentPowerL2() = powerL2
    override fun currentPowerL3() = powerL3
}

