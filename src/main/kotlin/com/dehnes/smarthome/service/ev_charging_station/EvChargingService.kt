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
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.roundToInt

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
                PilotVoltage.Volt_9 -> if (dataResponse.pwmPercent > 0) Connected_ChargingAvailable else Connected_ChargingUnavailable // TODO pwm depends on both the rate and the chargingState. Connected_ChargingAvailable gets a 12% duty cycle | 100 (which is lager than 0) means off!
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
    private val disregardStateAfterMs =
        persistenceService.get("chargingEndingTimeDeltaInMs", (1000 * 60 * 5).toString())!!.toLong()

    private val logger = KotlinLogging.logger { }

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
    ): InternalState? {
        return synchronized(this) {

            val existingState = currentData[evChargingStationClient.clientId] ?: InternalState(
                evChargingStationClient,
                ChargingState.reconstruct(dataResponse),
                System.currentTimeMillis(),
                dataResponse,
                pwmPercentToChargingRate(dataResponse.pwmPercent),
                null
            )

            /*
             * Did the last write() not succeed? re-send and give up
             */
            if (sendUpdates(evChargingStationClient.clientId, existingState, dataResponse)) return@synchronized null

            val mode = getMode(evChargingStationClient.clientId)
            val canCharge = mode == EvChargingMode.ON || tibberService.isEnergyPriceOK(persistenceService.get("PowerConnectionCheapestHours.${evChargingStationClient.powerConnectionId}", "4")!!.toInt())

            /*
             * State transitions needed?
             */
            val updatedState = when(existingState.chargingState) {
                ChargingState.Unconnected -> {
                    if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                        existingState.copy(chargingState = ChargingState.Error, dataResponse = dataResponse)
                    } else if (dataResponse.pilotVoltage != PilotVoltage.Volt_12) {
                        if (canCharge) {
                            existingState.copy(chargingState = ChargingState.Connected_ChargingAvailable, dataResponse = dataResponse)
                        } else {
                            existingState.copy(chargingState = ChargingState.Connected_ChargingUnavailable, dataResponse = dataResponse)
                        }
                    } else {
                        existingState.copy(dataResponse = dataResponse)
                    }
                }
                ChargingState.Connected_ChargingUnavailable -> {
                    if (canCharge)
                }
            }

            // TODO to this and others sendUpdates()


            TODO()


        }
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
        // pwm TODO pwm depend on both the rate and the charingState. Connected_ChargingAvailable gets a 12% duty cycle
        if (!isSame(dataResponse.pwmPercent, internalState.chargingRate)) {
            logger.info { "Re-sending pwm state to " + internalState.chargingState.contactorOn() }
            eVChargingStationConnection.setPwmPercent(
                clientId,
                chargingRateToPwmPercent(internalState.chargingRate)
            )
            sent = true
        }

        return sent

    }

}

fun chargingRateToPwmPercent(chargingRate: Int): Int {
    if (chargingRate == 0) {
        return 100
    }

    var amps = chargingRate
    if (amps < 6) {
        amps = 6
    }

    /*
     * 10% =>  6A
     * 15% => 10A
     * 25% => 16A
     * 40% => 25A
     * 50% => 32A
     *
     * PWM_percent = (1540X + 1500) / 1000
     */
    return (((1540 * amps) + 1500).toDouble() / 1000).roundToInt()
}

fun pwmPercentToChargingRate(pwmPercent: Int) = if (pwmPercent == 100) {
    0
} else {
    (((pwmPercent * 1000) - 1500).toDouble() / 1540).roundToInt()
}

fun isSame(pwmPercent: Int, chargingRate: Int): Boolean {
    val amps = pwmPercentToChargingRate(pwmPercent)
    return amps in (chargingRate - 1)..(chargingRate + 1)
}

data class InternalState(
    val evChargingStationClient: EvChargingStationClient,
    val chargingState: ChargingState,
    val chargingStateChangedAt: Long,

    val dataResponse: DataResponse,
    val chargingRate: Int,

    val measuredChargeRateLowSince: Long?
) {
    fun update(evChargingStationClient: EvChargingStationClient, dataResponse: DataResponse) = this.copy(
        evChargingStationClient = evChargingStationClient,
        dataResponse = dataResponse
    )

    fun export() = EvChargingStationData(
        chargingState,
        chargingStateChangedAt,
        dataResponse.proximityPilotAmps,
        chargingRate,
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