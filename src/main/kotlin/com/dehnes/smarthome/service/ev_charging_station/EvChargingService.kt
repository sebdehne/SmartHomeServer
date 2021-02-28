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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt

const val LOWEST_MAX_CHARGE_RATE = 6

enum class ChargingState {
    Unconnected, // 12V, no pwm, no contactor
    Connected_ChargingUnavailable, // 9V, no pwm, no contactor
    Connected_ChargingAvailable, // 9V, pwm, no contactor
    ChargingRequested, // 3V/6V, pwm, no contactor,
    Charging, // 3V/6V, pwm, contactor
    ChargingEnding, // 3V/6V, pwm, contactor + measured rate < (rate - chargingEndingAmpDelta) for chargingEndingTimeDeltaInMs
    StoppingCharging,
    Error;

    companion object {
        fun reconstruct(dataResponse: DataResponse): ChargingState {
            return when (dataResponse.pilotVoltage) {
                PilotVoltage.Volt_12 -> Unconnected
                PilotVoltage.Volt_9 -> if (dataResponse.pwmPercent < 100) Connected_ChargingAvailable else Connected_ChargingUnavailable
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
        Connected_ChargingAvailable, ChargingRequested, Charging, ChargingEnding, StoppingCharging -> true
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
    private val persistenceService: PersistenceService
) {
    val listeners = ConcurrentHashMap<String, (EvChargingEvent) -> Unit>()
    private val currentData = ConcurrentHashMap<String, InternalState>()

    // config
    private val chargingEndingAmpDelta = persistenceService.get("chargingEndingAmpDelta", "2")!!.toInt()
    private val chargingEndingTimeDeltaInMs =
        persistenceService.get("chargingEndingTimeDeltaInMs", (1000 * 60 * 5).toString())!!.toLong()
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


    private fun onIncomingDataUpdate(
        evChargingStationClient: EvChargingStationClient,
        dataResponse: DataResponse
    ) = synchronized(this) {
        val powerConnectionId = evChargingStationClient.powerConnectionId

        val existingState = currentData[evChargingStationClient.clientId] ?: run {
            val chargingState = ChargingState.reconstruct(dataResponse)
            InternalState(
                evChargingStationClient,
                chargingState,
                System.currentTimeMillis(),
                dataResponse,
                if (chargingState.pwmOn()) dataResponse.pwmPercentToChargingRate() else LOWEST_MAX_CHARGE_RATE,
                null,
                if (chargingState == Connected_ChargingUnavailable) "Unknown" else null
            )
        }

        /*
         * Did the last write() not succeed? re-send and give up if failed
         */
        if (!sendUpdates(evChargingStationClient.clientId, existingState, dataResponse)) return@synchronized null

        val mode = getMode(evChargingStationClient.clientId)
        val energyPriceOK = tibberService.isEnergyPriceOK(
            persistenceService.get(
                "CheapestHours.${evChargingStationClient.clientId}",
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
                        existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(Connected_ChargingUnavailable, LOWEST_MAX_CHARGE_RATE)
                            .setReasonChargingUnavailable(reasonCannotCharge)
                    }
                } else {
                    existingState
                }
            }
            Connected_ChargingUnavailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else {
                    if (canCharge) {
                        existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState
                    }
                }
            }
            Connected_ChargingAvailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState
                    } else {
                        existingState.changeState(Connected_ChargingUnavailable)
                            .setReasonChargingUnavailable(reasonCannotCharge)
                    }
                } else {
                    if (canCharge) {
                        existingState.changeState(ChargingRequested, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            Connected_ChargingUnavailable
                        ).setReasonChargingUnavailable(reasonCannotCharge)
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
                        existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(Connected_ChargingUnavailable)
                            .setReasonChargingUnavailable(reasonCannotCharge)
                    }
                } else {
                    if (System.currentTimeMillis() - existingState.chargingStateChangedAt > stayInStoppingChargingForMS) {
                        if (canCharge) {
                            existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                        } else {
                            existingState.changeState(Connected_ChargingUnavailable)
                                .setReasonChargingUnavailable(reasonCannotCharge)
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
                        existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(Connected_ChargingUnavailable)
                            .setReasonChargingUnavailable(reasonCannotCharge)
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
                        existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(Connected_ChargingUnavailable)
                            .setReasonChargingUnavailable(reasonCannotCharge)
                    }
                } else {
                    // still charging
                    var measuredChargeRateLowSince = existingState.measuredChargeRateLowSince

                    val isUsingLess =
                        dataResponse.measuredCurrentInAmp() < existingState.maxChargingRate - chargingEndingAmpDelta
                    if (!isUsingLess) {
                        measuredChargeRateLowSince = null
                    } else {
                        if (measuredChargeRateLowSince == null) {
                            measuredChargeRateLowSince = System.currentTimeMillis()
                        }
                    }
                    if (canCharge) {
                        if (measuredChargeRateLowSince != null && System.currentTimeMillis() - measuredChargeRateLowSince > chargingEndingTimeDeltaInMs) {
                            existingState.changeState(ChargingEnding)
                                .copy(measuredChargeRateLowSince = null)
                        } else {
                            existingState
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
                        existingState.changeState(Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(Connected_ChargingUnavailable)
                            .setReasonChargingUnavailable(reasonCannotCharge)
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

        currentData[evChargingStationClient.clientId] = updatedState

        // remove timed-out stations
        currentData.keys.toSet().forEach { key ->
            currentData.computeIfPresent(key) { _, internalState ->
                if (System.currentTimeMillis() - internalState.dataResponse.utcTimestampInMs > assumeStationLostAfterMs) {
                    null
                } else {
                    internalState
                }
            }
        }

        // (re-)calculate maxChargingRates
        val changedStates = calculateMaxChargingRates(powerConnectionId)

        val (decreasing, increasing) = changedStates.partition { newState ->
            val oldState = currentData[newState.evChargingStationClient.clientId]!!
            val contactorToOff = oldState.chargingState.contactorOn() && !newState.chargingState.contactorOn()
            val lowerRate = oldState.maxChargingRate > newState.maxChargingRate
            val pwmToOff = oldState.chargingState.pwmOn() && !newState.chargingState.pwmOn()

            contactorToOff || pwmToOff || lowerRate
        }

        val decreasingSuccess = decreasing.all { internalState ->
            sendUpdates(
                internalState.evChargingStationClient.clientId,
                internalState,
                internalState.dataResponse
            )
        }

        if (decreasingSuccess) {
            increasing.forEach { internalState ->
                sendUpdates(
                    internalState.evChargingStationClient.clientId,
                    internalState,
                    internalState.dataResponse
                )
            }
        }

        updatedState
    }

    private fun calculateMaxChargingRates(powerConnectionId: String): List<InternalState> {

        val changed = currentData
            .filter { it.value.evChargingStationClient.powerConnectionId == powerConnectionId }
            .toMap()
            .toMutableMap()

        var availableCapacity =
            persistenceService.get("powerConnectionId.availableCapacity.$powerConnectionId", "32")!!.toInt()
        check(availableCapacity in 1..32)

        val requestCapability = { request: Int ->
            check(request > 0)
            if (request <= availableCapacity) {
                availableCapacity -= request
                request
            } else {
                if (availableCapacity >= LOWEST_MAX_CHARGE_RATE) {
                    val smallerRequest = availableCapacity
                    availableCapacity -= smallerRequest
                    smallerRequest
                } else {
                    0
                }
            }
        }

        // Those stopping: set to lowest
        changed
            .filter { it.value.chargingState == StoppingCharging }
            .forEach { (clientId, internalState) ->
                val amps = requestCapability(LOWEST_MAX_CHARGE_RATE)
                if (amps > 0) {
                    changed[clientId] = internalState.setMaxChargeRate(amps)
                } else {
                    changed[clientId] = internalState.changeState(Connected_ChargingUnavailable)
                        .setReasonChargingUnavailable("Not enough capacity")
                }
            }

        // spread the remaining capacity evenly
        val stationsChargingAndEnding = changed
            .filter { it.value.chargingState.isChargingAndEnding() }
        val maxPerStation = availableCapacity / stationsChargingAndEnding.count()
        stationsChargingAndEnding.forEach { (clientId, internalState) ->
            val amps = requestCapability(maxPerStation)
            if (amps > 0) {
                changed[clientId] = internalState.setMaxChargeRate(amps)
            } else {
                changed[clientId] = internalState.changeState(Connected_ChargingUnavailable)
                    .setReasonChargingUnavailable("Not enough capacity")
            }
        }

        // reclaim from ending stations
        changed
            .filter { it.value.chargingState == ChargingEnding }
            .forEach { (clientId, internalState) ->
                val breakpoint = internalState.maxChargingRate - chargingEndingAmpDelta
                val capacityNotUsing = breakpoint - internalState.dataResponse.measuredCurrentInAmp()
                if (capacityNotUsing > 0) {
                    availableCapacity += capacityNotUsing
                    changed[clientId] =
                        internalState.setMaxChargeRate(internalState.maxChargingRate - capacityNotUsing)
                }
            }

        // give reclaimed to those charging
        if (availableCapacity > 0) {
            val stationsChargingButNotEnding = changed
                .filter { it.value.chargingState.isChargingButNotEnding() }
            val addToMaxChargingRate = availableCapacity / stationsChargingButNotEnding.count()
            stationsChargingButNotEnding.forEach { (clientId, internalState) ->
                val ampsToAdd = requestCapability(addToMaxChargingRate)
                changed[clientId] = internalState.setMaxChargeRate(internalState.maxChargingRate + ampsToAdd)
            }
        }

        // Move on from the requested-state
        changed
            .filter { it.value.chargingState == ChargingRequested }
            .forEach { (clientId, state) ->
                changed[clientId] = state.changeState(Charging, state.maxChargingRate)
            }

        return changed.values.toList()
    }

    private fun sendUpdates(clientId: String, internalState: InternalState, dataResponse: DataResponse): Boolean {
        var success = true

        // contactor
        if (internalState.chargingState.contactorOn() != dataResponse.conactorOn) {
            logger.info { "(Re-)sending contactor state to " + internalState.chargingState.contactorOn() }
            if (!eVChargingStationConnection.setContactorState(
                    clientId,
                    internalState.chargingState.contactorOn()
                )
            ) {
                success = false
            }
        }

        if (internalState.desiredPwmPercent() != dataResponse.pwmPercent) {
            logger.info { "(Re-)sending pwm state to " + internalState.chargingState.contactorOn() }
            if (!eVChargingStationConnection.setPwmPercent(
                    clientId,
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
        maxChargingRate: Int? = null
    ): InternalState {
        if (chargingState.pwmOn()) {
            check(maxChargingRate != null)
        }
        if (chargingState == Connected_ChargingUnavailable) {
            check(reasonChargingUnavailable != null)
        }

        return if (this.chargingState == chargingState) {
            this
        } else {
            logger.info { "${this.evChargingStationClient.clientId} changed state from ${this.maxChargingRate} to $chargingState" }
            this.copy(
                chargingState = chargingState,
                chargingStateChangedAt = System.currentTimeMillis(),
                maxChargingRate = maxChargingRate ?: this.maxChargingRate,
                reasonChargingUnavailable = if (chargingState != Connected_ChargingUnavailable) null else this.reasonChargingUnavailable
            )
        }
    }

}

data class InternalState(
    val evChargingStationClient: EvChargingStationClient,
    val chargingState: ChargingState,
    val chargingStateChangedAt: Long,

    val dataResponse: DataResponse,
    val maxChargingRate: Int, // a value in the range og 6..32

    val measuredChargeRateLowSince: Long?,
    val reasonChargingUnavailable: String?
) {

    fun desiredPwmPercent() = if (chargingState.pwmOn()) {
        /*
         * 10% =>  6A
         * 15% => 10A
         * 25% => 16A
         * 40% => 25A
         * 50% => 32A
         *
         * PWM_percent = (1540X + 1500) / 1000
         */
        (((1540 * maxChargingRate) + 1500).toDouble() / 1000).roundToInt()

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
