package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.energy_pricing.tibber.TibberService
import com.dehnes.smarthome.ev_charging.ChargingState.*
import com.dehnes.smarthome.utils.PersistenceService
import mu.KotlinLogging
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt

const val LOWEST_MAX_CHARGE_RATE = 6

enum class ChargingState {
    // 12V, PWM off, Contactor off
    Unconnected,

    // 9V, PWM off, Contactor off - used when charging is not allowed
    ConnectedChargingUnavailable,

    // 9V, PWM on, Contactor off - used when charging is allowed
    ConnectedChargingAvailable,

    // 3V/6V, PWM on, Contactor off - car is ready - need to re-balance load sharing before allowing to proceed
    ChargingRequested,

    // 3V/6V, PWM on, Contactor on - charging
    Charging,

    // 3V/6V, PWM on, Contactor on - Car is charging, but charging is not allowed anymore, maxCharging rate is at minimum to allow protect Contactor
    StoppingCharging,

    // Pilot voltage not 12, 9, 6 or 3 Volt. PWM off, Contactor off - cannot proceed.
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
        Charging, StoppingCharging -> true
        else -> false
    }

    fun pwmOn() = when (this) {
        ConnectedChargingAvailable, ChargingRequested, Charging -> true
        else -> false
    }
}

class EvChargingService(
    private val eVChargingStationConnection: EvChargingStationConnection,
    private val executorService: ExecutorService,
    private val tibberService: TibberService,
    private val persistenceService: PersistenceService,
    private val clock: Clock,
    private val loadSharingAlgorithms: Map<String, LoadSharing>
) {
    val listeners = ConcurrentHashMap<String, (EvChargingEvent) -> Unit>()
    private val currentData = ConcurrentHashMap<String, InternalState>()

    // config
    private val chargingEndingAmpDelta =
        persistenceService["EvChargingService.chargingEndingAmpDelta", "2"]!!.toInt()
    private val stayInStoppingChargingForMS =
        persistenceService["EvChargingService.stayInStoppingChargingForMS", (1000 * 5).toString()]!!.toLong()
    private val assumeStationLostAfterMs =
        persistenceService["EvChargingService.assumeStationLostAfterMs", (1000 * 60 * 5).toString()]!!.toLong()
    private val rollingAverageDepth = 5

    val logger = KotlinLogging.logger { }

    fun start() {

        // start listening for incoming events from the charging stations
        eVChargingStationConnection.listeners[this::class.qualifiedName!!] = { event ->
            when (event.eventType) {
                EventType.clientData -> {
                    onIncomingDataUpdate(event.evChargingStationClient, event.clientData!!)?.let { updatedState ->
                        executorService.submit {
                            listeners.forEach { (_, fn) ->
                                fn(
                                    EvChargingEvent(
                                        EvChargingEventType.chargingStationDataAndConfig,
                                        getChargingStationsDataAndConfig()
                                    )
                                )
                            }
                        }
                    }
                }
                else -> logger.debug { "Ignored ${event.eventType}" }
            }
        }
    }

    fun getConnectedClients() = eVChargingStationConnection.getConnectedClients()

    fun getChargingStationsDataAndConfig() = currentData.map { entry ->
        toEvChargingStationDataAndConfig(entry.value)
    }

    fun updateMode(clientId: String, evChargingMode: EvChargingMode): Boolean {
        persistenceService["EvChargingService.client.mode.$clientId"] = evChargingMode.name
        eVChargingStationConnection.collectDataAndDistribute(clientId)
        return true
    }

    fun getMode(clientId: String) =
        persistenceService.get("EvChargingService.client.mode.$clientId", EvChargingMode.ChargeDuringCheapHours.name)!!
            .let { EvChargingMode.valueOf(it) }

    fun getNumberOfHoursRequiredFor(clientId: String) = persistenceService.get(
        "EvChargingService.client.numberOfHoursRequired.$clientId",
        "4"
    )!!.toInt()

    fun setNumberOfHoursRequiredFor(clientId: String, numberOfHoursRequiredFor: Int): Boolean {
        persistenceService.set(
            "EvChargingService.client.numberOfHoursRequired.$clientId",
            numberOfHoursRequiredFor.toString()
        )
        return true
    }

    fun setPriorityFor(clientId: String, loadSharingPriority: LoadSharingPriority) = synchronized(this) {
        persistenceService.set("EvChargingService.client.priorty.$clientId", loadSharingPriority.name)
        var result = false
        currentData.computeIfPresent(clientId) { _, internalState ->
            result = true
            internalState.copy(loadSharingPriority = loadSharingPriority)
        }
        result
    }

    fun getPriorityFor(clientId: String) =
        persistenceService.get("EvChargingService.client.priorty.$clientId", LoadSharingPriority.NORMAL.name)!!.let {
            LoadSharingPriority.valueOf(it)
        }


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
                getPriorityFor(clientId),
                dataResponse,
                if (chargingState.pwmOn()) dataResponse.pwmPercentToChargingRate() else LOWEST_MAX_CHARGE_RATE,
                null,
                null,
                if (chargingState == ConnectedChargingUnavailable) "Unknown" else null
            )
        }
        val powerConnectionId = existingState.powerConnectionId

        /*
         * A) If some received data doesnt match the required state, re-send and stop
         */
        if (!synchronizeIfNeeded(existingState, dataResponse)) return@synchronized null

        /*
         * B) Figure out if the charging stations is allowed to charge at this point in time
         */
        val mode = getMode(clientId)
        val nextCheapHour = tibberService.mustWaitUntil(getNumberOfHoursRequiredFor(clientId))
        var reasonCannotCharge: String? = null
        val canCharge = when {
            mode == EvChargingMode.ON -> true
            mode == EvChargingMode.OFF -> {
                reasonCannotCharge = "Switched Off"
                false
            }
            mode == EvChargingMode.ChargeDuringCheapHours && nextCheapHour == null -> true
            mode == EvChargingMode.ChargeDuringCheapHours && nextCheapHour != null -> {
                reasonCannotCharge = "starting @ " + nextCheapHour.atZone(clock.zone).toLocalTime()
                false
            }
            else -> error("Impossible")
        }

        /*
         * C) Handle Charging-state transitions
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
                    if (canCharge) {
                        val measuredCurrentInAmps = (
                                existingState.measuredCurrentInAmps
                                    ?: dataResponse.currentInAmps()).let { avg ->
                            ((avg * rollingAverageDepth) + dataResponse.currentInAmps()) / (rollingAverageDepth + 1)
                        }

                        val measuredCurrentPeakAt =
                            if (existingState.measuredCurrentInAmps == null || measuredCurrentInAmps > existingState.measuredCurrentInAmps)
                                clock.millis()
                            else
                                existingState.measuredCurrentPeakAt

                        existingState.copy(
                            measuredCurrentInAmps = measuredCurrentInAmps,
                            measuredCurrentPeakAt = measuredCurrentPeakAt
                        )
                    } else {
                        existingState.changeState(StoppingCharging, LOWEST_MAX_CHARGE_RATE)
                    }
                }
            }
        }
            .updateData(evChargingStationClient, dataResponse)
            .copy(reasonChargingUnavailable = reasonCannotCharge)

        currentData[clientId] = updatedState

        /*
         * D) Remove timed-out stations - do not consider for load sharing anymore
         */
        currentData.keys.toSet().forEach { key ->
            currentData.computeIfPresent(key) { _, internalState ->
                if (clock.millis() - internalState.dataResponse.utcTimestampInMs > assumeStationLostAfterMs) {
                    null
                } else {
                    internalState
                }
            }
        }

        /*
         * E) Rebalance loadsharing between active charging stations
         */
        val loadSharingAlgorithmId =
            persistenceService["EvChargingService.powerConnection.loadSharingAlgorithm.$powerConnectionId", PriorityLoadSharing::class.java.simpleName]!!
        val loadSharingAlgorithm = loadSharingAlgorithms[loadSharingAlgorithmId]
            ?: error("Could not find loadSharingAlgorithmId=$loadSharingAlgorithmId")
        val changedStates = loadSharingAlgorithm.calculateLoadSharing(
            currentData,
            powerConnectionId,
            chargingEndingAmpDelta
        ) as List<InternalState>

        /*
         * F) Send out updates, but send those which should give up capacity (decrease) first and about if this failed
         */
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

        // only send increments of all decrements were successful
        if (decreasingSuccess) {
            increasing.forEach { internalState ->
                synchronizeIfNeeded(internalState)
            }
        }

        updatedState
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

        // PWM signal (maxChargingRate)
        if (internalState.desiredPwmPercent() != data.pwmPercent) {
            logger.info { "(Re-)sending pwm state to " + internalState.desiredPwmPercent() }
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
            this.copy(
                maxChargingRate = newMaxChargeRate,
                chargingState = chargingState,
                chargingStateChangedAt = clock.millis(),
                reasonChargingUnavailable = if (chargingState == ConnectedChargingUnavailable) reasonChargingUnavailable else null,
                measuredCurrentInAmps = null,
                measuredCurrentPeakAt = null
            )
        }
    }

    private fun toEvChargingStationDataAndConfig(internalState: InternalState) = EvChargingStationDataAndConfig(
        internalState.export(),
        EVChargingStationConfig(
            getMode(internalState.clientId),
            internalState.loadSharingPriority,
            getNumberOfHoursRequiredFor(internalState.clientId)
        ),
        internalState.evChargingStationClient
    )
}

data class InternalState(
    val clientId: String,
    override val powerConnectionId: String,
    val evChargingStationClient: EvChargingStationClient,
    override val chargingState: ChargingState,
    val chargingStateChangedAt: Long,
    val loadSharingPriority: LoadSharingPriority,

    val dataResponse: DataResponse,
    override val maxChargingRate: Int, // a value in the range og 6..32

    override val measuredCurrentPeakAt: Long?,
    override val measuredCurrentInAmps: Int?,

    val reasonChargingUnavailable: String?
) : LoadSharable {

    override val proximityPilotAmps: Int
        get() = dataResponse.proximityPilotAmps.toAmps()
    override val loadSharingPriorityValue: Int
        get() = loadSharingPriority.value

    override fun setNoCapacityAvailable(timestamp: Long) = if (chargingState != ConnectedChargingUnavailable)
        copy(
            chargingState = ConnectedChargingUnavailable,
            reasonChargingUnavailable = "No capacity available",
            chargingStateChangedAt = timestamp,
            measuredCurrentPeakAt = null,
            measuredCurrentInAmps = null
        )
    else
        this

    override fun allowChargingWith(maxChargingRate: Int, timestamp: Long) =
        if (this.chargingState == ChargingRequested) copy(
            chargingState = Charging,
            chargingStateChangedAt = timestamp,
            maxChargingRate = maxChargingRate,
            measuredCurrentInAmps = null,
            measuredCurrentPeakAt = null
        )
        else copy(
            maxChargingRate = maxChargingRate
        )

    fun desiredPwmPercent() = if (chargingState.pwmOn()) {
        chargeRateToPwmPercent(maxChargingRate)
    } else {
        100
    }

    fun updateData(evChargingStationClient: EvChargingStationClient, dataResponse: DataResponse) =
        if (dataResponse.pwmPercent > this.dataResponse.pwmPercent) copy(
            evChargingStationClient = evChargingStationClient,
            dataResponse = dataResponse,
            measuredCurrentPeakAt = null,
            measuredCurrentInAmps = null
        ) else copy(
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
        dataResponse.wifiRSSI,
        dataResponse.utcTimestampInMs
    )

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
