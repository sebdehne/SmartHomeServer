package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.ev_charging.ChargingState.*
import com.dehnes.smarthome.utils.PersistenceService
import java.time.Clock


interface LoadSharing {
    fun calculateLoadSharing(
        currentData: Map<String, LoadSharable>,
        powerConnectionId: String,
        chargingEndingAmpDelta: Int
    ): List<LoadSharable>
}

interface LoadSharable {
    val powerConnectionId: String
    val chargingState: ChargingState
    val proximityPilotAmps: Int
    val measuredCurrentInAmp: Int
    val maxChargingRate: Int
    val loadSharingPriorityValue: Int

    fun setNoCapacityAvailable(timestamp: Long): LoadSharable
    fun allowChargingWith(maxChargingRate: Int, timestamp: Long): LoadSharable
}

enum class LoadSharingPriority(
    val value: Int
) {
    HIGH(1),
    NORMAL(2),
    LOW(3)
}

/*
 * Shares the available capacity evenly among those with the same priority; starting at the highest and going downwards if
 * more capacity is available
 */
class PriorityLoadSharing(
    private val persistenceService: PersistenceService,
    private val clock: Clock
) : LoadSharing {

    override fun calculateLoadSharing(
        currentData: Map<String, LoadSharable>,
        powerConnectionId: String,
        chargingEndingAmpDelta: Int
    ): List<LoadSharable> {

        /*
         * Setup
         */
        val loadSharableById =
            currentData.filter { it.value.powerConnectionId == powerConnectionId }.toMap().toMutableMap()
        var availableCapacity =
            persistenceService["PowerConnection.availableCapacity.$powerConnectionId", "32"]!!.toInt()
        check(availableCapacity in 1..1000) { "AvailableCapacity outside of reasonable range? $availableCapacity" }

        val requestCapability = { request: Int ->
            request.coerceAtMost(availableCapacity).apply {
                availableCapacity -= this
            }
        }

        val spreadAvailableCapacity = { isInitial: Boolean, byPriority: Map<Int, Map<String, LoadSharable>> ->
            if (byPriority.isNotEmpty()) {
                byPriority.keys.sorted().forEach { priority ->
                    val stations = byPriority[priority]!!

                    val capacityPerStation = availableCapacity / stations.count()

                    stations.forEach { (clientId, internalState) ->
                        if (isInitial) {
                            val possibleChargeRate = capacityPerStation.coerceAtMost(internalState.proximityPilotAmps)
                            val amps = requestCapability(possibleChargeRate.coerceAtLeast(LOWEST_MAX_CHARGE_RATE))
                            if (amps > 0) {
                                loadSharableById[clientId] = internalState.allowChargingWith(amps, clock.millis())
                            } else {
                                loadSharableById[clientId] = internalState.setNoCapacityAvailable(clock.millis())
                            }
                        } else {
                            val headRoom = internalState.proximityPilotAmps - internalState.maxChargingRate
                            val ampsToAdd = requestCapability(capacityPerStation.coerceAtMost(headRoom))
                            loadSharableById[clientId] =
                                internalState.allowChargingWith(
                                    internalState.maxChargingRate + ampsToAdd,
                                    clock.millis()
                                )
                        }
                    }
                }
            }
        }

        /*
         * States in which capacity is needed: StoppingCharging, ChargingRequested, Charging & ChargingEnding
         */

        // Those which are in StoppingCharging state - set to lowest maxChargingRate - no matter which priority
        loadSharableById
            .filter { it.value.chargingState == StoppingCharging }
            .forEach { (clientId, internalState) ->
                val amps = requestCapability(LOWEST_MAX_CHARGE_RATE)
                if (amps > 0) {
                    loadSharableById[clientId] = internalState.allowChargingWith(amps, clock.millis())
                } else {
                    loadSharableById[clientId] = internalState.setNoCapacityAvailable(clock.millis())
                }
            }

        // initially: for each priority-level, spread the capacity evenly
        spreadAvailableCapacity(true, sortByPriority(
            loadSharableById.filter {
                it.value.chargingState in listOf(Charging, ChargingEnding, ChargingRequested)
            }
        ))

        // Then reclaim capacity from ChargingEnding stations
        loadSharableById
            .filter { it.value.chargingState == ChargingEnding }
            .forEach { (clientId, internalState) ->

                if (internalState.maxChargingRate > LOWEST_MAX_CHARGE_RATE) {
                    val breakpoint = internalState.maxChargingRate - chargingEndingAmpDelta
                    val capacityNotUsing = breakpoint - internalState.measuredCurrentInAmp
                    if (capacityNotUsing > 0) {

                        val newRate = if (internalState.maxChargingRate - capacityNotUsing < LOWEST_MAX_CHARGE_RATE) {
                            LOWEST_MAX_CHARGE_RATE
                        } else {
                            internalState.maxChargingRate - capacityNotUsing
                        }
                        val madeAvailable = internalState.maxChargingRate - newRate

                        if (madeAvailable > 0) {
                            availableCapacity += madeAvailable
                            loadSharableById[clientId] =
                                internalState.allowChargingWith(
                                    internalState.maxChargingRate - madeAvailable,
                                    clock.millis()
                                )
                        }
                    }
                }
            }

        // At last: give reclaimed capacity to those which are capable of getting more
        while (true) {
            val stationsWhichWantMore = loadSharableById.filter {
                it.value.chargingState in listOf(
                    Charging,
                    ChargingRequested
                ) && it.value.proximityPilotAmps > it.value.maxChargingRate
            }

            if (stationsWhichWantMore.isEmpty() || availableCapacity == 0) {
                break
            }

            spreadAvailableCapacity(false, sortByPriority(stationsWhichWantMore))
        }

        return loadSharableById.values.toList()

    }

    private fun sortByPriority(input: Map<String, LoadSharable>) = input.toList()
        .groupBy { it.second.loadSharingPriorityValue }
        .mapValues { entry ->
            entry.value.toMap()
        }

}