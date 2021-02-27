package com.dehnes.smarthome.service.ev_charging_station

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.external.ev_charging_station.DataResponse
import com.dehnes.smarthome.external.ev_charging_station.EVChargingStationConnection
import com.dehnes.smarthome.external.ev_charging_station.EventType
import com.dehnes.smarthome.external.ev_charging_station.PilotVoltage
import com.dehnes.smarthome.service.PersistenceService
import com.dehnes.smarthome.service.TibberService
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

        val existingState = currentData[evChargingStationClient.clientId] ?: run {
            val chargingState = ChargingState.reconstruct(dataResponse)
            InternalState(
                evChargingStationClient,
                chargingState,
                System.currentTimeMillis(),
                dataResponse,
                if (chargingState.pwmOn()) dataResponse.pwmPercentToChargingRate() else LOWEST_MAX_CHARGE_RATE,
                null
            )
        }

        /*
         * Did the last write() not succeed? re-send and give up
         */
        if (sendUpdates(evChargingStationClient.clientId, existingState, dataResponse)) return@synchronized null

        val mode = getMode(evChargingStationClient.clientId)
        val canCharge = mode == EvChargingMode.ON || tibberService.isEnergyPriceOK(
            persistenceService.get(
                "PowerConnectionCheapestHours.${evChargingStationClient.powerConnectionId}",
                "4"
            )!!.toInt()
        )

        /*
         * State transitions needed?
         */
        val updatedState = when (existingState.chargingState) {
            ChargingState.Unconnected -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error, LOWEST_MAX_CHARGE_RATE)
                } else if (dataResponse.pilotVoltage != PilotVoltage.Volt_12) {
                    if (canCharge) {
                        existingState.changeState(ChargingState.Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(ChargingState.Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    }
                } else {
                    existingState
                }
            }
            ChargingState.Connected_ChargingUnavailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error)
                } else {
                    if (canCharge) {
                        existingState.changeState(ChargingState.Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState
                    }
                }
            }
            ChargingState.Connected_ChargingAvailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (!canCharge) {
                        existingState.changeState(
                            ChargingState.Connected_ChargingUnavailable
                        )
                    } else {
                        existingState
                    }
                } else {
                    if (canCharge) {
                        existingState.changeState(ChargingState.ChargingRequested, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(
                            ChargingState.Connected_ChargingUnavailable
                        )
                    }
                }
            }
            ChargingState.Error -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else {
                    existingState
                }
            }
            ChargingState.StoppingCharging -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    existingState.changeState(ChargingState.Connected_ChargingUnavailable)
                } else {
                    if (System.currentTimeMillis() - existingState.chargingStateChangedAt > stayInStoppingChargingForMS) {
                        existingState.changeState(ChargingState.Connected_ChargingUnavailable)
                    } else {
                        existingState
                    }
                }
            }
            ChargingState.ChargingRequested -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ChargingState.Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(ChargingState.Connected_ChargingUnavailable)
                    }
                } else {
                    existingState
                }
            }
            ChargingState.Charging -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ChargingState.Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(ChargingState.Connected_ChargingUnavailable)
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
                            existingState.changeState(ChargingState.ChargingEnding)
                                .copy(measuredChargeRateLowSince = null)
                        } else {
                            existingState
                        }
                    } else {
                        existingState.changeState(ChargingState.StoppingCharging, LOWEST_MAX_CHARGE_RATE)
                    }
                }
            }
            ChargingState.ChargingEnding -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(ChargingState.Unconnected)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(ChargingState.Error)
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    if (canCharge) {
                        existingState.changeState(ChargingState.Connected_ChargingAvailable, LOWEST_MAX_CHARGE_RATE)
                    } else {
                        existingState.changeState(ChargingState.Connected_ChargingUnavailable)
                    }
                } else {
                    if (canCharge) {
                        existingState
                    } else {
                        existingState.changeState(ChargingState.StoppingCharging, LOWEST_MAX_CHARGE_RATE)
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
        val maxChargingRates = calculateMaxChargingRates(evChargingStationClient.powerConnectionId)

        // store updated rates
        maxChargingRates.forEach { (clientId, maxChargingRate) ->
            currentData.computeIfPresent(clientId) { _, existingState ->
                existingState.copy(maxChargingRate = maxChargingRate)
            }
        }

        // send out updates
        currentData.forEach { (clientId, state) ->
            sendUpdates(clientId, state, state.dataResponse)
        }

        updatedState
    }

    private fun calculateMaxChargingRates(powerConnectionId: String): Map<String, Int> {
        val connectedStations =
            currentData.values.filter { it.evChargingStationClient.powerConnectionId == powerConnectionId }

        // TODO take into account:
        // - proximity signal
        // - can take capacity from ChargingEnding-stations?

        //


        TODO()
    }

    private fun sendUpdates(clientId: String, internalState: InternalState, dataResponse: DataResponse): Boolean {
        var sent = false

        // contactor
        if (internalState.chargingState.contactorOn() != dataResponse.conactorOn) {
            logger.info { "Re-sending contactor state to " + internalState.chargingState.contactorOn() }
            eVChargingStationConnection.setContactorState(
                clientId,
                internalState.chargingState.contactorOn()
            )
            sent = true
        }

        if (internalState.desiredPwmPercent() != dataResponse.pwmPercent) {
            logger.info { "Re-sending pwm state to " + internalState.chargingState.contactorOn() }
            eVChargingStationConnection.setPwmPercent(
                clientId,
                internalState.desiredPwmPercent()
            )
            sent = true
        }

        return sent
    }

    private fun InternalState.changeState(chargingState: ChargingState, maxChargingRate: Int? = null): InternalState {
        if (chargingState.pwmOn()) {
            check(maxChargingRate != null)
        }

        logger.info { "${this.evChargingStationClient.clientId} changed state from ${this.maxChargingRate} to $chargingState" }

        return this.copy(
            chargingState = chargingState,
            chargingStateChangedAt = System.currentTimeMillis(),
            maxChargingRate = maxChargingRate ?: this.maxChargingRate
        )
    }

}

data class InternalState(
    val evChargingStationClient: EvChargingStationClient,
    val chargingState: ChargingState,
    val chargingStateChangedAt: Long,

    val dataResponse: DataResponse,
    val maxChargingRate: Int, // a value in the range og 6..32

    val measuredChargeRateLowSince: Long?
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

    fun export() = EvChargingStationData(
        chargingState,
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
}

fun DataResponse.pwmPercentToChargingRate() = (((pwmPercent * 1000) - 1500).toDouble() / 1540).roundToInt()
