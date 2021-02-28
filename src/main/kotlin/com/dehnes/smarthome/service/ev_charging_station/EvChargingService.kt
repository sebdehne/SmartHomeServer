package com.dehnes.smarthome.service.ev_charging_station

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.external.ev_charging_station.DataResponse
import com.dehnes.smarthome.external.ev_charging_station.EVChargingStationConnection
import com.dehnes.smarthome.external.ev_charging_station.EventType
import com.dehnes.smarthome.external.ev_charging_station.PilotVoltage
import com.dehnes.smarthome.service.PersistenceService
import com.dehnes.smarthome.service.TibberService
import com.dehnes.smarthome.service.ev_charging_station.ChargingState.*
import mu.KotlinLogging
import java.lang.Integer.max
import java.lang.Integer.min
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt

const val LOWEST_MAX_CHARGE_RATE = 6

enum class ChargingState {
    Unconnected, // 12V, no pwm, no contactor
    ConnectedChargingUnavailable, // 9V, no pwm, no contactor
    ConnectedChargingAvailable, // 9V, pwm, no contactor
    ChargingRequested, // 3V/6V, pwm, no contactor,
    Charging, // 3V/6V, pwm, contactor
    ChargingEnding, // 3V/6V, pwm, contactor + measured rate < (rate - chargingEndingAmpDelta) for chargingEndingTimeDeltaInMs
    StoppingCharging,
    Error;

    companion object {
        fun reconstruct(dataResponse: DataResponse): ChargingState {
            return when (dataResponse.pilotVoltage) {
                PilotVoltage.Volt_12 -> Unconnected
                PilotVoltage.Volt_9 -> if (dataResponse.pwmPercent < 100) ConnectedChargingAvailable else ConnectedChargingUnavailable
                PilotVoltage.Volt_6 -> if (dataResponse.conactorOn) Charging else ChargingRequested
                PilotVoltage.Volt_3 -> if (dataResponse.conactorOn) Charging else ChargingRequested
                PilotVoltage.Fault -> Error
            }
        }
    }

    fun contactorOn() = when (this) {
        Charging, ChargingEnding, StoppingCharging -> true
        else -> false
    }

    fun pwmOn() = when (this) {
        ConnectedChargingAvailable, ChargingRequested, Charging, ChargingEnding, StoppingCharging -> true
        else -> false
    }

    fun isChargingAndEnding() = when (this) {
        Charging, ChargingEnding, ChargingRequested -> true
        else -> false
    }

    fun isChargingButNotEnding() = when (this) {
        Charging, ChargingRequested -> true
        else -> false
    }
}

class EvChargingService(
    private val eVChargingStationConnection: EVChargingStationConnection,
    private val executorService: ExecutorService,
    private val tibberService: TibberService,
    private val persistenceService: PersistenceService,
    private val clock: Clock
) {
    val listeners = ConcurrentHashMap<String, (EvChargingEvent) -> Unit>()
    private val currentData = ConcurrentHashMap<String, InternalState>()

    // config
    private val chargingEndingAmpDelta = persistenceService.get("chargingEndingAmpDelta", "2")!!.toInt()
    private val stayInStoppingChargingForMS =
        persistenceService.get("stayInStoppingChargingForMS", (1000 * 5).toString())!!.toLong()
    private val assumeStationLostAfterMs =
        persistenceService.get("assumeStationLostAfterMs", (1000 * 60 * 5).toString())!!.toLong()

    val logger = KotlinLogging.logger { }

    fun start() {

        eVChargingStationConnection.listeners[this::class.qualifiedName!!] = { event ->
            when (event.eventType) {
                EventType.newClientConnection -> executorService.submit {
                    listeners.forEach { (_, fn) ->
                        fn(EvChargingEvent(EvChargingEventType.newConnection, event.evChargingStationClient, null))
                    }
                }
                EventType.closedClientConnection -> {
                    executorService.submit {
                        listeners.forEach { (_, fn) ->
                            fn(
                                EvChargingEvent(
                                    EvChargingEventType.closedConnection,
                                    event.evChargingStationClient,
                                    null
                                )
                            )
                        }
                    }
                }
                EventType.clientData -> {
                    onIncomingDataUpdate(event.evChargingStationClient, event.clientData!!)?.let { updatedState ->
                        executorService.submit {
                            listeners.forEach { (_, fn) ->
                                fn(
                                    EvChargingEvent(
                                        EvChargingEventType.data,
                                        event.evChargingStationClient,
                                        updatedState.export()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun getConnectedClients() = eVChargingStationConnection.getConnectedClients()
    fun getData(clientId: String) = currentData[clientId]?.export()

    fun updateMode(clientId: String, evChargingMode: EvChargingMode) {
        persistenceService["EvChargingMode.$clientId"] = evChargingMode.name
    }

    fun getMode(clientId: String) =
        persistenceService.get("EvChargingMode.$clientId", EvChargingMode.ChargeDuringCheapHours.name)!!
            .let { EvChargingMode.valueOf(it) }


    internal fun onIncomingDataUpdate(
        evChargingStationClient: EvChargingStationClient,
        dataResponse: DataResponse
    ) = synchronized(this) {

        val clientId = evChargingStationClient.clientId

        val existingState = currentData[clientId] ?: run {
            val chargingState = ChargingState.reconstruct(dataResponse)
            InternalState(
                clientId,
                evChargingStationClient.powerConnectionId,
                evChargingStationClient,
                chargingState,
                clock.millis(),
                dataResponse,
                if (chargingState.pwmOn()) dataResponse.pwmPercentToChargingRate() else LOWEST_MAX_CHARGE_RATE,
                null,
                if (chargingState == ConnectedChargingUnavailable) "Unknown" else null
            )
        }
        val powerConnectionId = existingState.powerConnectionId

        // If some received data doesnt match the required state, re-send and stop
        if (!synchronizeIfNeeded(existingState, dataResponse)) return@synchronized null

        val mode = getMode(clientId)
        val energyPriceOK = tibberService.isEnergyPriceOK(
            persistenceService.get(
                "CheapestHours.$clientId",
                "4"
            )!!.toInt()
        )
        var reasonCannotCharge: String? = null
        val canCharge = when {
            mode == EvChargingMode.ON -> true
            mode == EvChargingMode.OFF -> {
                reasonCannotCharge = "Manually switched off"
                false
            }
            mode == EvChargingMode.ChargeDuringCheapHours && energyPriceOK -> true
            mode == EvChargingMode.ChargeDuringCheapHours && !energyPriceOK -> {
                reasonCannotCharge = "Waiting for lower energy prices"
                false
            }
            else -> error("Impossible")
        }

        /*
         * State transitions needed?
         */
        val updatedState = when (existingState.chargingState) {
            Unconnected -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, LOWEST_MAX_CHARGE_RATE)
                } else if (dataResponse.pilotVoltage != PilotVoltage.Volt_12) {
                    if (canCharge) {
                        existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            LOWEST_MAX_CHARGE_RATE,
                            reasonCannotCharge
                        )
                    }
                } else {
                    existingState
                }
            }
            ConnectedChargingUnavailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else {
                    if (canCharge) {
                        existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState
                    }
                }
            }
            ConnectedChargingAvailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            reasonChargingUnavailable = reasonCannotCharge
                        )
                    }
                } else {
                    if (canCharge) {
                        existingState.changeState(ChargingRequested, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            reasonChargingUnavailable = reasonCannotCharge
                        )
                    }
                }
            }
            Error -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else {
                    existingState
                }
            }
            StoppingCharging -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            reasonChargingUnavailable = reasonCannotCharge
                        )
                    }
                } else {
                    if (clock.millis() - existingState.chargingStateChangedAt >= stayInStoppingChargingForMS) {
                        if (canCharge) {
                            existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                        } else {
                            existingState.changeState(
                                ConnectedChargingUnavailable,
                                reasonChargingUnavailable = reasonCannotCharge
                            )
                        }
                    } else {
                        existingState
                    }
                }
            }
            ChargingRequested -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            reasonChargingUnavailable = reasonCannotCharge
                        )
                    }
                } else {
                    existingState
                }
            }
            Charging -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(ConnectedChargingUnavailable)
                            .setReasonChargingUnavailable(reasonCannotCharge)
                    }
                } else {
                    // still charging
                    var goesToEnding = false
                    var measuredChargeRatePeak = existingState.measuredChargeRatePeak
                    if (measuredChargeRatePeak == null || dataResponse.measuredCurrentInAmp() >= measuredChargeRatePeak) {
                        measuredChargeRatePeak = dataResponse.measuredCurrentInAmp()
                    } else if (dataResponse.measuredCurrentInAmp() < measuredChargeRatePeak - chargingEndingAmpDelta) {
                        goesToEnding = true
                    }

                    if (canCharge) {
                        if (goesToEnding) {
                            existingState.changeState(ChargingEnding, existingState.maxChargingRate)
                                .copy(measuredChargeRatePeak = null)
                        } else {
                            existingState.copy(measuredChargeRatePeak = measuredChargeRatePeak)
                        }
                    } else {
                        existingState.changeState(StoppingCharging, LOWEST_MAX_CHARGE_RATE)
                    }
                }
            }
            ChargingEnding -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ConnectedChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            reasonChargingUnavailable = reasonCannotCharge
                        )
                    }
                } else {
                    if (canCharge) {
                        existingState
                    } else {
                        existingState.changeState(StoppingCharging, LOWEST_MAX_CHARGE_RATE)
                    }
                }
            }
        }.updateData(evChargingStationClient, dataResponse)

        currentData[clientId] = updatedState

        // remove timed-out stations
        currentData.keys.toSet().forEach { key ->
            currentData.computeIfPresent(key) { _, internalState ->
                if (clock.millis() - internalState.dataResponse.utcTimestampInMs > assumeStationLostAfterMs) {
                    null
                } else {
                    internalState
                }
            }
        }

        // (re-)calculate maxChargingRates
        val changedStates = calculateMaxChargingRates(powerConnectionId)

        val (decreasing, increasing) = changedStates.partition { newState ->
            val oldState = currentData[newState.clientId]!!
            val contactorToOff = oldState.chargingState.contactorOn() && !newState.chargingState.contactorOn()
            val lowerRate = oldState.maxChargingRate > newState.maxChargingRate
            val pwmToOff = oldState.chargingState.pwmOn() && !newState.chargingState.pwmOn()

            // update cache
            currentData[newState.clientId] = newState

            contactorToOff || pwmToOff || lowerRate
        }

        val decreasingSuccess = decreasing.all { internalState ->
            synchronizeIfNeeded(internalState)
        }

        if (decreasingSuccess) {
            increasing.forEach { internalState ->
                synchronizeIfNeeded(internalState)
            }
        }

        updatedState
    }

    private fun calculateMaxChargingRates(powerConnectionId: String): List<InternalState> {

        val changed = currentData
            .filter { it.value.powerConnectionId == powerConnectionId }
            .toMap()
            .toMutableMap()

        var availableCapacity =
            persistenceService.get("powerConnectionId.availableCapacity.$powerConnectionId", "32")!!.toInt()
        check(availableCapacity in 1..32)

        val requestCapability = { request: Int ->
            val requestNormalized = max(LOWEST_MAX_CHARGE_RATE, request)
            val canGive = min(availableCapacity, requestNormalized)
            availableCapacity -= canGive
            canGive
        }

        // Those stopping: set to lowest
        changed
            .filter { it.value.chargingState == StoppingCharging }
            .forEach { (clientId, internalState) ->
                val amps = requestCapability(LOWEST_MAX_CHARGE_RATE)
                if (amps > 0) {
                    changed[clientId] = internalState.setMaxChargeRate(amps)
                } else {
                    changed[clientId] = internalState.changeState(
                        ConnectedChargingUnavailable,
                        reasonChargingUnavailable = "Not enough capacity"
                    )
                }
            }

        // spread the remaining capacity evenly
        val stationsChargingAndEnding = changed
            .filter { it.value.chargingState.isChargingAndEnding() }
        if (stationsChargingAndEnding.isNotEmpty()) {
            val maxPerStation = availableCapacity / stationsChargingAndEnding.count()
            stationsChargingAndEnding.forEach { (clientId, internalState) ->
                val amps = requestCapability(max(LOWEST_MAX_CHARGE_RATE, maxPerStation))
                if (amps > 0) {
                    changed[clientId] = internalState.setMaxChargeRate(amps)
                } else {
                    changed[clientId] = internalState.changeState(
                        ConnectedChargingUnavailable,
                        reasonChargingUnavailable = "Not enough capacity"
                    )
                }
            }
        }

        // reclaim from stations which are not using their capacity (ending or proximity)
        changed
            .filter { it.value.chargingState == ChargingEnding || it.value.maxChargingRate > it.value.dataResponse.proximityPilotAmps.toAmps() }
            .forEach { (clientId, internalState) ->
                var state = internalState

                if (state.maxChargingRate > state.dataResponse.proximityPilotAmps.toAmps()) {
                    val cannotUse = state.maxChargingRate - state.dataResponse.proximityPilotAmps.toAmps()
                    availableCapacity += cannotUse
                    state = state.setMaxChargeRate(state.maxChargingRate - cannotUse)
                }

                if (state.maxChargingRate > LOWEST_MAX_CHARGE_RATE) {
                    val breakpoint = state.maxChargingRate - chargingEndingAmpDelta
                    val capacityNotUsing = breakpoint - state.dataResponse.measuredCurrentInAmp()
                    if (capacityNotUsing > 0) {

                        val newRate = if (state.maxChargingRate - capacityNotUsing < LOWEST_MAX_CHARGE_RATE) {
                            LOWEST_MAX_CHARGE_RATE
                        } else {
                            state.maxChargingRate - capacityNotUsing
                        }
                        val madeAvailable = state.maxChargingRate - newRate

                        if (madeAvailable > 0) {
                            availableCapacity += madeAvailable
                            state = state.setMaxChargeRate(state.maxChargingRate - madeAvailable)
                        }
                    }
                }

                changed[clientId] = state
            }

        // give reclaimed to those which are capable of getting more
        while (true) {
            val stationsWhichWantMore = changed
                .filter { it.value.chargingState.isChargingButNotEnding() && it.value.dataResponse.proximityPilotAmps.toAmps() > it.value.maxChargingRate }

            if (stationsWhichWantMore.isEmpty() || availableCapacity == 0) {
                break
            }

            val addToMaxChargingRate = availableCapacity / stationsWhichWantMore.count()

            stationsWhichWantMore.forEach { (clientId, internalState) ->
                val headRoom = internalState.dataResponse.proximityPilotAmps.toAmps() - internalState.maxChargingRate
                val ampsToAdd = requestCapability(min(headRoom, addToMaxChargingRate))
                changed[clientId] = internalState.setMaxChargeRate(internalState.maxChargingRate + ampsToAdd)
            }
        }

        // Move onwards from the requested-state
        changed
            .filter { it.value.chargingState == ChargingRequested }
            .forEach { (clientId, state) ->
                changed[clientId] = state.changeState(Charging, state.maxChargingRate)
            }

        return changed.values.toList()
    }

    private fun synchronizeIfNeeded(internalState: InternalState, dataToCompareAgainst: DataResponse? = null): Boolean {
        var success = true

        val data = dataToCompareAgainst ?: internalState.dataResponse

        // contactor
        if (internalState.chargingState.contactorOn() != data.conactorOn) {
            logger.info { "(Re-)sending contactor state to " + internalState.chargingState.contactorOn() }
            if (!eVChargingStationConnection.setContactorState(
                    internalState.clientId,
                    internalState.chargingState.contactorOn()
                )
            ) {
                success = false
            }
        }

        if (internalState.desiredPwmPercent() != data.pwmPercent) {
            logger.info { "(Re-)sending pwm state to " + internalState.chargingState.contactorOn() }
            if (!eVChargingStationConnection.setPwmPercent(
                    internalState.clientId,
                    internalState.desiredPwmPercent()
                )
            ) {
                success = false
            }
        }

        return success
    }

    private fun InternalState.changeState(
        chargingState: ChargingState,
        maxChargingRate: Int? = null,
        reasonChargingUnavailable: String? = null
    ): InternalState {
        if (chargingState.pwmOn()) {
            check(maxChargingRate != null)
        }
        if (chargingState == ConnectedChargingUnavailable) {
            check(reasonChargingUnavailable != null)
        }

        return if (this.chargingState == chargingState) {
            this
        } else {
            val newMaxChargeRate = maxChargingRate ?: this.maxChargingRate
            logger.info { "StateChange for ${this.clientId}:  ${this.chargingState} -> $chargingState [maxChargingRate=${this.maxChargingRate} -> $newMaxChargeRate]" }
            this.setMaxChargeRate(newMaxChargeRate)
                .copy(
                    chargingState = chargingState,
                    chargingStateChangedAt = clock.millis(),
                    reasonChargingUnavailable = if (chargingState == ConnectedChargingUnavailable) reasonChargingUnavailable else null
                )
        }
    }

}

data class InternalState(
    val clientId: String,
    val powerConnectionId: String,
    val evChargingStationClient: EvChargingStationClient,
    val chargingState: ChargingState,
    val chargingStateChangedAt: Long,

    val dataResponse: DataResponse,
    val maxChargingRate: Int, // a value in the range og 6..32

    val measuredChargeRatePeak: Int?,

    val reasonChargingUnavailable: String?
) {

    fun desiredPwmPercent() = if (chargingState.pwmOn()) {
        chargeRateToPwmPercent(maxChargingRate)
    } else {
        100
    }

    fun updateData(evChargingStationClient: EvChargingStationClient, dataResponse: DataResponse) = copy(
        evChargingStationClient = evChargingStationClient,
        dataResponse = dataResponse
    )

    fun setReasonChargingUnavailable(reasonChargingUnavailable: String?) = copy(
        reasonChargingUnavailable = reasonChargingUnavailable
    )

    fun export() = EvChargingStationData(
        chargingState,
        reasonChargingUnavailable,
        chargingStateChangedAt,
        dataResponse.proximityPilotAmps,
        maxChargingRate,
        dataResponse.phase1Millivolts,
        dataResponse.phase2Millivolts,
        dataResponse.phase3Millivolts,
        dataResponse.phase1Milliamps,
        dataResponse.phase2Milliamps,
        dataResponse.phase3Milliamps,
        dataResponse.systemUptime,
        dataResponse.utcTimestampInMs
    )

    fun setMaxChargeRate(amps: Int) = copy(maxChargingRate = amps)
}

fun DataResponse.pwmPercentToChargingRate() = (((pwmPercent * 1000) - 1500).toDouble() / 1540).roundToInt()

/*
 * 10% =>  6A
 * 15% => 10A
 * 25% => 16A
 * 40% => 25A
 * 50% => 32A
 *
 * PWM_percent = (1540X + 1500) / 1000
 */
fun chargeRateToPwmPercent(maxChargingRate: Int) = (((1540 * maxChargingRate) + 1500).toDouble() / 1000).roundToInt()
