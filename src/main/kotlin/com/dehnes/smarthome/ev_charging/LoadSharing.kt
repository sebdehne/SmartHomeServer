package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.ev_charging.ChargingState.*
import com.dehnes.smarthome.utils.PersistenceService


interface LoadSharing {
    fun calculateLoadSharing(currentData: Map<String, LoadSharable>, powerConnectionId: String): List<LoadSharable>
}

interface LoadSharable {
    val powerConnectionId: String
    val chargingState: ChargingState
    val proximityPilotAmps: Int
    val measuredCurrentInAmp: Int
    val maxChargingRate: Int

    fun setNoCapacityAvailable(): LoadSharable
    fun allowChargingWith(maxChargingRate: Int): LoadSharable
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
    private val persistenceService: PersistenceService
) : LoadSharing {

    private val chargingEndingAmpDelta = persistenceService.get("chargingEndingAmpDelta", "2")!!.toInt()

    fun setPriorityFor(clientId: String, loadSharingPriority: LoadSharingPriority) {
        persistenceService.set("PriorityLoadSharing.priority.$clientId", loadSharingPriority.name)
    }

    fun getPriorityFor(clientId: String) =
        persistenceService.get("PriorityLoadSharing.priority.$clientId", LoadSharingPriority.NORMAL.name)!!.let {
            LoadSharingPriority.valueOf(it)
        }

    override fun calculateLoadSharing(
        currentData: Map<String, LoadSharable>,
        powerConnectionId: String
    ): List<LoadSharable> {

        /*
         * Setup
         */
        val loadSharableById =
            currentData.filter { it.value.powerConnectionId == powerConnectionId }.toMap().toMutableMap()
        var availableCapacity =
            persistenceService.get("powerConnectionId.availableCapacity.$powerConnectionId", "32")!!.toInt()
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
                                loadSharableById[clientId] = internalState.allowChargingWith(amps)
                            } else {
                                loadSharableById[clientId] = internalState.setNoCapacityAvailable()
                            }
                        } else {
                            val headRoom = internalState.proximityPilotAmps - internalState.maxChargingRate
                            val ampsToAdd = requestCapability(capacityPerStation.coerceAtMost(headRoom))
                            loadSharableById[clientId] =
                                internalState.allowChargingWith(internalState.maxChargingRate + ampsToAdd)
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
                    loadSharableById[clientId] = internalState.allowChargingWith(amps)
                } else {
                    loadSharableById[clientId] = internalState.setNoCapacityAvailable()
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
                                internalState.allowChargingWith(internalState.maxChargingRate - madeAvailable)
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
        .groupBy { getPriorityFor(it.first).value }
        .mapValues { entry ->
            entry.value.toMap()
        }

}