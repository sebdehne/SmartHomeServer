package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.ev_charging.ChargingState.*
import com.dehnes.smarthome.utils.PersistenceService
import java.lang.Integer.max
import java.time.Clock

const val LOWEST_MAX_CHARGE_RATE = 6

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
    val loadSharingPriorityValue: Int

    val measuredCurrentInAmps: Int?
    val measuredCurrentPeakAt: Long?

    fun getMaxChargeCurrentAmps(): Int
    fun setNoCapacityAvailable(timestamp: Long): LoadSharable
    fun adjustMaxChargeCurrent(amps: Int, timestamp: Long): LoadSharable
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
                                loadSharableById[clientId] = internalState.adjustMaxChargeCurrent(amps, clock.millis())
                            } else {
                                loadSharableById[clientId] = internalState.setNoCapacityAvailable(clock.millis())
                            }
                        } else {
                            val headRoom = internalState.proximityPilotAmps - internalState.getMaxChargeCurrentAmps()
                            val ampsToAdd = requestCapability(capacityPerStation.coerceAtMost(headRoom))
                            loadSharableById[clientId] =
                                internalState.adjustMaxChargeCurrent(
                                    internalState.getMaxChargeCurrentAmps() + ampsToAdd,
                                    clock.millis()
                                )
                        }
                    }
                }
            }
        }

        /*
         * States in which capacity is needed: ChargingRequested, Charging
         */

        // initially: for each priority-level, spread the capacity evenly
        spreadAvailableCapacity(true, sortByPriority(
            loadSharableById.filter {
                it.value.chargingState in listOf(Charging, ChargingRequested)
            }
        ))

        // Then reclaim capacity from unused capacity
        val stationsNotUsingTheirCapacity = mutableSetOf<String>()
        loadSharableById
            .filter { it.value.chargingState == Charging }
            .filter { it.value.measuredCurrentInAmps != null && it.value.measuredCurrentPeakAt != null }
            .filter { clock.millis() - it.value.measuredCurrentPeakAt!! > 60 * 1000 }
            .forEach { (clientId, internalState) ->

                if (internalState.getMaxChargeCurrentAmps() > LOWEST_MAX_CHARGE_RATE) {
                    val breakpoint = internalState.getMaxChargeCurrentAmps() - chargingEndingAmpDelta
                    val capacityNotUsing = breakpoint - internalState.measuredCurrentInAmps!!
                    if (capacityNotUsing > 0) {

                        val newRate = max(LOWEST_MAX_CHARGE_RATE, internalState.getMaxChargeCurrentAmps() - capacityNotUsing)
                        val madeAvailable = internalState.getMaxChargeCurrentAmps() - newRate

                        if (madeAvailable > 0) {
                            availableCapacity += madeAvailable
                            stationsNotUsingTheirCapacity.add(clientId)
                            loadSharableById[clientId] =
                                internalState.adjustMaxChargeCurrent(
                                    internalState.getMaxChargeCurrentAmps() - madeAvailable,
                                    clock.millis()
                                )
                        }
                    }
                }
            }

        // At last: give reclaimed capacity to those which are capable of getting more
        while (true) {
            val stationsWhichWantMore = loadSharableById.filter {
                it.value.chargingState in listOf(Charging, ChargingRequested)
                        && it.value.proximityPilotAmps > it.value.getMaxChargeCurrentAmps()
                        && it.key !in stationsNotUsingTheirCapacity
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