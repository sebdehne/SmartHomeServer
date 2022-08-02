package com.dehnes.smarthome.ev_charging

import java.time.Clock

const val LOWEST_CHARGE_RATE = 6

interface LoadSharing {
    fun calculateLoadSharing(
        currentData: List<LoadSharable>,
        powerConnection: PowerConnection,
        chargingEndingAmpDelta: Int
    ): List<LoadSharable>
}

interface LoadSharable {
    val clientId: String
    val loadSharingPriority: LoadSharingPriority
    val usesThreePhase: Boolean

    fun isCharging(): Boolean
    fun isChargingOrChargingRequested(): Boolean

    fun getConfiguredRateAmps(): Int

    fun wantsMore(timestamp: Long): Boolean

    fun setNoCapacityAvailable(timestamp: Long): LoadSharable
    fun adjustMaxChargeCurrent(amps: Int, timestamp: Long): LoadSharable
}

interface PowerConnection {
    fun availableAmpsCapacity(): Int
    fun availablePowerCapacity(): Int
}

enum class LoadSharingPriority {
    HIGH,
    NORMAL,
    LOW
}

/*
 * Shares the available capacity evenly among those with the same priority; starting at the highest and going downwards if
 * more capacity is available
 */
class PriorityLoadSharing(
    private val clock: Clock
) : LoadSharing {

    override fun calculateLoadSharing(
        currentData: List<LoadSharable>,
        powerConnection: PowerConnection,
        chargingEndingAmpDelta: Int
    ): List<LoadSharable> {

        /*
         * Setup
         */
        val loadSharableById = currentData.map { it.clientId to it }.toMap().toMutableMap()
        var availableAmpsCapacity = powerConnection.availableAmpsCapacity()
        check(availableAmpsCapacity in 0..1000) { "AvailableCapacity outside of reasonable range? $availableAmpsCapacity" }
        var availablePowerCapacity = powerConnection.availablePowerCapacity()

        val takeCapacity = { requestedAmps: Int, threePhase: Boolean ->

            val phases = if (threePhase) 3 else 1
            val requestedPower = (requestedAmps * 230) * phases

            val hasEnoughAmps = requestedAmps <= availableAmpsCapacity
            val hasEnoughPower = requestedPower <= availablePowerCapacity

            if (hasEnoughAmps && hasEnoughPower) {
                availableAmpsCapacity -= requestedAmps
                availablePowerCapacity -= requestedPower
                requestedAmps
            } else {
                0
            }
        }

        // Initially - all zero
        loadSharableById.values
            .filter { it.isChargingOrChargingRequested() }
            .forEach { ls ->
                loadSharableById[ls.clientId] = ls.adjustMaxChargeCurrent(0, clock.millis())
            }

        // Spread the capacity
        LoadSharingPriority.values().forEach { loadSharingPriority ->

            var somethingChanged: Boolean
            while (true) {
                somethingChanged = false
                loadSharableById.values
                    .filter { it.loadSharingPriority == loadSharingPriority }
                    .filter { it.isChargingOrChargingRequested() }
                    .sortedBy { it.clientId }
                    .filter { it.wantsMore(clock.millis()) }
                    .forEach { sh ->
                        val toBeAdded = if (sh.getConfiguredRateAmps() == 0) {
                            LOWEST_CHARGE_RATE
                        } else {
                            1
                        }
                        val addedAmps = takeCapacity(toBeAdded, sh.usesThreePhase)
                        if (addedAmps > 0) {
                            loadSharableById[sh.clientId] = sh.adjustMaxChargeCurrent(
                                sh.getConfiguredRateAmps() + addedAmps,
                                clock.millis()
                            )
                            somethingChanged = true
                        }
                    }

                if (!somethingChanged) {
                    break
                }
            }
        }

        // those which remain at zero -> update state
        loadSharableById.values
            .filter { it.isChargingOrChargingRequested() }
            .forEach { ls ->
                if (ls.getConfiguredRateAmps() < LOWEST_CHARGE_RATE) {
                    loadSharableById[ls.clientId] = ls.setNoCapacityAvailable(clock.millis())
                }
            }

        return loadSharableById.values.toList()
    }

}