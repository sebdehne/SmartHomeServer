package com.dehnes.smarthome.han

import com.dehnes.smarthome.enery.EnergyUnit
import com.dehnes.smarthome.enery.Milliwatts
import java.time.Instant

class GridMonitor : EnergyUnit {

    private var lastUpdate = Instant.MIN
    private var currentPowerL1: Milliwatts = 0
    private var currentPowerL2: Milliwatts = 0
    private var currentPowerL3: Milliwatts = 0

    fun onNewHanData(data: HanData) {
        synchronized(this) {
            lastUpdate = Instant.now()
            currentPowerL1 = data.currentL1 * data.voltageL1
            currentPowerL2 = data.currentL2 * data.voltageL2
            currentPowerL3 = data.currentL3 * data.voltageL3
        }
    }

    override val id: String
        get() = "Norgesnett"
    override val name: String
        get() = "Norgesnett"

    override fun currentPowerL1(): Milliwatts = ifUp2Date(currentPowerL1)

    override fun currentPowerL2(): Milliwatts = ifUp2Date(currentPowerL2)

    override fun currentPowerL3(): Milliwatts = ifUp2Date(currentPowerL3)

    private fun ifUp2Date(value: Milliwatts) = if (lastUpdate.plusSeconds(20).isAfter(Instant.now()))
        value
    else
        0

}