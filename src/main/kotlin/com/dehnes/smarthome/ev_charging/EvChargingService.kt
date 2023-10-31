package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.EvCharger
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.PriceCategory
import com.dehnes.smarthome.energy_pricing.priceDecision
import com.dehnes.smarthome.ev_charging.ChargingState.*
import com.dehnes.smarthome.users.SystemUser
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.DateTimeUtils
import com.dehnes.smarthome.utils.withLogging
import com.dehnes.smarthome.victron.VictronService
import mu.KotlinLogging
import java.lang.Integer.min
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.math.ceil
import kotlin.math.roundToInt

const val PWM_OFF = 100
const val PWM_NO_CHARGING = 100

enum class ChargingState {
    // 12V, PWM off, Contactor off
    Unconnected,

    // 9V, PWM off, Contactor off - used when charging is not allowed
    ConnectedChargingUnavailable,

    // 9V, PWM on (=> 10%), Contactor off - used when charging is allowed
    ConnectedChargingAvailable,

    // 3V/6V, PWM on (=> 10%), Contactor off - car is ready - need to re-balance load sharing before allowing to proceed
    ChargingRequested,

    // 3V/6V, PWM on (=> 10%), Contactor on - charging
    Charging,

    // 3V/6V, PWM on (= 3%), Contactor on - Car is charging, but charging is not allowed anymore, maxCharging rate is at minimum to allow protect Contactor
    StoppingCharging,

    // Pilot voltage not 12, 9, 6 or 3 Volt. PWM off, Contactor off - cannot proceed.
    Error;

    companion object {
        fun reconstruct(dataResponse: DataResponse): ChargingState {
            return when (dataResponse.pilotVoltage) {
                PilotVoltage.Volt_12 -> Unconnected
                PilotVoltage.Volt_9 -> pwmToChargingState(dataResponse.pwmPercent)
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
}

class EvChargingService(
    private val eVChargingStationConnection: EvChargingStationConnection,
    private val executorService: ExecutorService,
    private val energyPriceService: EnergyPriceService,
    private val configService: ConfigService,
    private val clock: Clock,
    private val loadSharingAlgorithms: Map<String, LoadSharing>,
    private val victronService: VictronService,
    private val userSettingsService: UserSettingsService,
    private val energyConsumptionService: EnergyConsumptionService,
) {
    private val listeners = ConcurrentHashMap<String, (EvChargingEvent) -> Unit>()
    private val currentData = ConcurrentHashMap<String, InternalState>()

    // config
    private val chargingEndingAmpDelta = configService.getEvSettings().chargingEndingAmpDelta
    private val stayInStoppingChargingForMS = configService.getEvSettings().stayInStoppingChargingForMS
    private val assumeStationLostAfterMs = configService.getEvSettings().assumeStationLostAfterMs
    private val rollingAverageDepth = 5

    companion object {
        val logger = KotlinLogging.logger { }
    }

    fun start() {

        // start listening for incoming events from the charging stations
        eVChargingStationConnection.listeners[this::class.qualifiedName!!] = { event ->
            when (event.eventType) {
                EventType.clientData -> {
                    onIncomingDataUpdate(event.evChargingStationClient, event.clientData!!)?.let { updatedState ->
                        executorService.submit(withLogging {

                            recordPower(updatedState)

                            listeners.forEach { (_, fn) ->
                                fn(
                                    EvChargingEvent(
                                        EvChargingEventType.chargingStationDataAndConfig,
                                        getChargingStationsDataAndConfig(SystemUser)
                                    )
                                )
                            }
                        })
                    }
                }

                else -> logger.debug { "Ignored ${event.eventType}" }
            }
        }
    }

    fun addListener(user: String?, id: String, listener: (EvChargingEvent) -> Unit) {
        if (!userSettingsService.canUserRead(user, UserRole.evCharging)) return
        listeners[id] = listener
    }

    fun removeListener(id: String) {
        listeners.remove(id)
    }

    fun getChargingStationsDataAndConfig(user: String?): List<EvChargingStationDataAndConfig> {
        if (!userSettingsService.canUserRead(user, UserRole.evCharging)) return emptyList()
        return currentData.map { entry ->
            toEvChargingStationDataAndConfig(entry.value)
        }
    }

    fun updateMode(user: String?, clientId: String, evChargingMode: EvChargingMode): Boolean {
        if (!userSettingsService.canUserWrite(user, UserRole.evCharging)) return false
        setChargerSettings(
            getChargerSettings(clientId).copy(
                evChargingMode = evChargingMode
            )
        )
        eVChargingStationConnection.collectDataAndDistribute(clientId)
        return true
    }

    fun setPriorityFor(user: String?, clientId: String, loadSharingPriority: LoadSharingPriority) = synchronized(this) {
        if (!userSettingsService.canUserWrite(user, UserRole.evCharging)) return@synchronized false

        val evCharger = getChargerSettings(clientId)
        setChargerSettings(
            evCharger.copy(priority = loadSharingPriority)
        )

        var result = false
        currentData.computeIfPresent(clientId) { _, internalState ->
            result = true
            internalState.copy(loadSharingPriority = loadSharingPriority)
        }
        result
    }

    fun setChargeRateLimitFor(user: String?, clientId: String, chargeRateLimit: Int) = synchronized(this) {
        if (!userSettingsService.canUserWrite(user, UserRole.evCharging)) return@synchronized false

        check(chargeRateLimit in 6..32)

        val settings = getChargerSettings(clientId)
        setChargerSettings(
            settings.copy(
                chargeRateLimit = chargeRateLimit
            )
        )

        var result = false
        currentData.computeIfPresent(clientId) { _, internalState ->
            result = true
            internalState.copy(chargeRateLimit = chargeRateLimit)
        }
        result
    }

    private fun getChargerSettings(clientId: String) =
        configService.getEvSettings().chargers.entries.firstOrNull { it.value.name == clientId }?.value
            ?: error("Fant ingen chargerSetings for $clientId")

    private fun setChargerSettings(evCharger: EvCharger) {
        val evSettings = configService.getEvSettings()
        configService.setEvSettings(
            evSettings.copy(
                chargers = evSettings.chargers + (evCharger.serialNumber to evCharger)
            )
        )
    }

    private fun getMode(clientId: String) = getChargerSettings(clientId).evChargingMode

    private fun recordPower(updatedState: InternalState) {
        if (updatedState.isCharging()) {
            val ampsL1 = updatedState.dataResponse.phase1Milliamps.toDouble() / 1000
            val ampsL2 = updatedState.dataResponse.phase2Milliamps.toDouble() / 1000
            val ampsL3 = updatedState.dataResponse.phase3Milliamps.toDouble() / 1000
            val totalAmps = listOfNotNull(
                if (ampsL1 > 2) ampsL1 else 0.0,
                if (ampsL2 > 2) ampsL2 else 0.0,
                if (ampsL3 > 2) ampsL3 else 0.0,
            ).sum()

            energyConsumptionService.reportPower(
                "EvCharger${updatedState.clientId}",
                totalAmps * 230
            )

        } else {
            energyConsumptionService.reportPower(
                "EvCharger${updatedState.clientId}",
                0.0
            )
        }
    }

    private fun getPriorityFor(clientId: String) = getChargerSettings(clientId).priority

    private fun getChargeRateLimit(clientId: String) = getChargerSettings(clientId).chargeRateLimit

    private fun getPowerConnection(powerConnectionId: String): PowerConnection = object : PowerConnection {
        override fun availableAmpsCapacity(): Int {
            return getPowerConnectionSettings(powerConnectionId).availableCapacity
        }

        override fun availablePowerCapacity(): Int {
            return (230 * availableAmpsCapacity()) * 3 // TODO ony provide the headroom up to the configured power-target
        }
    }

    private fun getPowerConnectionSettings(powerConnectionId: String) =
        configService.getEvSettings().powerConnections[powerConnectionId]
            ?: error("Fant ingen powerConnection=$powerConnectionId")

    internal fun onIncomingDataUpdate(
        evChargingStationClient: EvChargingStationClient,
        dataResponse: DataResponse
    ) = synchronized(this) {

        val clientId = evChargingStationClient.clientId

        val existingState = currentData[clientId] ?: run {
            val chargingState = ChargingState.reconstruct(dataResponse)
            InternalState(
                clientId = clientId,
                powerConnectionId = evChargingStationClient.powerConnectionId,
                evChargingStationClient = evChargingStationClient,
                chargingState = chargingState,
                chargingStateChangedAt = clock.millis(),
                loadSharingPriority = getPriorityFor(clientId),
                chargeRateLimit = getChargeRateLimit(clientId),
                usesThreePhase = false,
                dataResponse = dataResponse,
                pwmDutyCyclePercent = dataResponse.pwmPercent,
                measuredCurrentPeakAt = null,
                measuredCurrentInAmpsAvg = null,
                reasonChargingUnavailable = if (chargingState == ConnectedChargingUnavailable) "Unknown" else null,
                chargingEndingAmpDelta = chargingEndingAmpDelta
            )
        }
        val powerConnectionId = existingState.powerConnectionId
        val loadSharingAlgorithmId = getPowerConnectionSettings(powerConnectionId).loadSharingAlgorithm
        val loadSharingAlgorithm = loadSharingAlgorithms[loadSharingAlgorithmId]
            ?: error("Could not find loadSharingAlgorithmId=$loadSharingAlgorithmId")

        /*
         * A) If some received data doesn't match the required state, re-send and stop
         */
        if (!synchronizeIfNeeded(existingState, dataResponse)) return@synchronized null

        /*
         * B) Figure out if charging is allowed to charge at this point in time
         */
        val getReasonCannotCharge = {
            val mode = getMode(clientId)
            val gridOk = victronService.isGridOk()
            val suitablePrices =
                energyPriceService.findSuitablePrices(SystemUser, "EvCharger$clientId", LocalDate.now(DateTimeUtils.zoneId))
            val priceDecision = suitablePrices.priceDecision()
            var reasonCannotCharge: String? = null

            when {
                mode == EvChargingMode.OFF -> {
                    reasonCannotCharge = "Switched Off"
                }

                mode == EvChargingMode.ChargeDuringCheapHours && !gridOk -> {
                    reasonCannotCharge = "Grid is offline"
                }

                mode == EvChargingMode.ChargeDuringCheapHours && (priceDecision?.current != PriceCategory.cheap) -> {
                    reasonCannotCharge = "starting @ " + priceDecision?.changesAt?.atZone(clock.zone)?.toLocalTime()
                }
            }

            // ask load sharing algorithm if there is capacity available
            if (reasonCannotCharge == null) {
                val changedStates = loadSharingAlgorithm.calculateLoadSharing(
                    (currentData + (clientId to existingState.changeState(
                        ChargingRequested,
                        clock.millis(),
                        fake = true
                    ))).values.toList(),
                    getPowerConnection(powerConnectionId),
                    chargingEndingAmpDelta
                ) as List<InternalState>
                reasonCannotCharge = changedStates.first { it.clientId == clientId }.reasonChargingUnavailable
            }

            reasonCannotCharge
        }

        /*
         * C) Handle Charging-state transitions
         */
        val updatedState = when (existingState.chargingState) {
            Unconnected -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, clock.millis())
                } else if (dataResponse.pilotVoltage != PilotVoltage.Volt_12) {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState.changeState(ConnectedChargingAvailable, clock.millis())
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            clock.millis(),
                            reasonCannotCharge
                        )
                    }
                } else {
                    existingState
                }
            }

            ConnectedChargingUnavailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, clock.millis())
                } else {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState.changeState(ConnectedChargingAvailable, clock.millis())
                    } else {
                        existingState.changeState(ConnectedChargingUnavailable, clock.millis(), reasonCannotCharge)
                    }
                }
            }

            ConnectedChargingAvailable -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            clock.millis(),
                            reasonCannotCharge
                        )
                    }
                } else {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState.changeState(ChargingRequested, clock.millis())
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            clock.millis(),
                            reasonCannotCharge
                        )
                    }
                }
            }

            Error -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected, clock.millis())
                } else {
                    existingState
                }
            }

            StoppingCharging -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState.changeState(ConnectedChargingAvailable, clock.millis())
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            clock.millis(),
                            reasonCannotCharge
                        )
                    }
                } else {
                    if (clock.millis() - existingState.chargingStateChangedAt < stayInStoppingChargingForMS) {
                        existingState
                    } else {
                        val reasonCannotCharge = getReasonCannotCharge()
                        if (reasonCannotCharge == null) {
                            existingState.changeState(ConnectedChargingAvailable, clock.millis())
                        } else {
                            existingState.changeState(
                                ConnectedChargingUnavailable,
                                clock.millis(),
                                reasonCannotCharge
                            )
                        }
                    }
                }
            }

            ChargingRequested -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState.changeState(ConnectedChargingAvailable, clock.millis())
                    } else {
                        existingState.changeState(
                            ConnectedChargingUnavailable,
                            clock.millis(),
                            reasonCannotCharge
                        )
                    }
                } else {
                    existingState
                }
            }

            Charging -> {
                if (dataResponse.pilotVoltage == PilotVoltage.Volt_12) {
                    existingState.changeState(Unconnected, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Fault) {
                    existingState.changeState(Error, clock.millis())
                } else if (dataResponse.pilotVoltage == PilotVoltage.Volt_9) {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        existingState.changeState(ConnectedChargingAvailable, clock.millis())
                    } else {
                        existingState.changeState(ConnectedChargingUnavailable, clock.millis())
                            .setReasonChargingUnavailable(reasonCannotCharge)
                    }
                } else {
                    val reasonCannotCharge = getReasonCannotCharge()
                    if (reasonCannotCharge == null) {
                        val existingMeasuredCurrentInAmpsAvg =
                            existingState.measuredCurrentInAmpsAvg ?: dataResponse.currentInAmps().toDouble()
                        val updatedMeasuredCurrentInAmpsAvg =
                            ((existingMeasuredCurrentInAmpsAvg * rollingAverageDepth) + dataResponse.currentInAmps()) / (rollingAverageDepth + 1)

                        val measuredCurrentPeakAt =
                            if (existingState.measuredCurrentPeakAt == null || updatedMeasuredCurrentInAmpsAvg > (existingMeasuredCurrentInAmpsAvg + 0.2))
                                clock.millis()
                            else
                                existingState.measuredCurrentPeakAt

                        logger.debug {
                            "Charger=$clientId measuredCurrentPeakAt=${
                                Instant.ofEpochMilli(
                                    measuredCurrentPeakAt
                                )
                            } measuredCurrentInAmpsAvg=$updatedMeasuredCurrentInAmpsAvg"
                        }

                        existingState.copy(
                            measuredCurrentInAmpsAvg = updatedMeasuredCurrentInAmpsAvg,
                            measuredCurrentPeakAt = measuredCurrentPeakAt
                        )
                    } else {
                        existingState.changeState(StoppingCharging, clock.millis(), reasonCannotCharge)
                    }
                }
            }
        }
            .updateData(evChargingStationClient, dataResponse)
            .let {
                if (!it.usesThreePhase) {
                    val p2Amps = dataResponse.phase2Milliamps / 1000
                    val p3Amps = dataResponse.phase3Milliamps / 1000
                    if (p2Amps > 2 || p3Amps > 2) {
                        logger.info { "Detected three phase charging for clientId=$clientId" }
                        // current flowing over P2 / P3 -> upgrade to three phase
                        it.copy(usesThreePhase = true)
                    } else {
                        it
                    }
                } else {
                    it
                }
            }

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
         * E) Re-balance loadsharing between active charging stations
         */
        val changedStates = loadSharingAlgorithm.calculateLoadSharing(
            currentData.values.toList(),
            getPowerConnection(powerConnectionId),
            chargingEndingAmpDelta
        ) as List<InternalState>

        /*
         * F) Send out updates, but send those which should give up capacity (decrease) first and about if this failed
         */
        val (decreasing, increasing) = changedStates.partition { newState ->
            val oldState = currentData[newState.clientId]!!
            val contactorToOff = oldState.chargingState.contactorOn() && !newState.chargingState.contactorOn()
            val lowerRate = oldState.pwmDutyCyclePercent > newState.pwmDutyCyclePercent
            val pwmToOff = oldState.pwmDutyCyclePercent != 100 && newState.pwmDutyCyclePercent == 100

            // update cache
            currentData[newState.clientId] = newState

            contactorToOff || pwmToOff || lowerRate
        }

        val decreasingSuccess = decreasing.all { internalState ->
            synchronizeIfNeeded(internalState)
        }

        // only send increments if all decrements were successful
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
        val desiredPwmPercent = desiredPwmPercent(internalState.chargingState, internalState.pwmDutyCyclePercent)
        if (desiredPwmPercent != data.pwmPercent) {
            logger.info { "(Re-)sending pwm state to $desiredPwmPercent" }
            if (!eVChargingStationConnection.setPwmPercent(
                    internalState.clientId,
                    desiredPwmPercent
                )
            ) {
                success = false
            }
        }

        return success
    }

    private fun toEvChargingStationDataAndConfig(internalState: InternalState) = EvChargingStationDataAndConfig(
        internalState.export(),
        EVChargingStationConfig(
            getMode(internalState.clientId),
            internalState.loadSharingPriority,
            getChargeRateLimit(internalState.clientId)
        ),
        internalState.evChargingStationClient
    )
}

data class InternalState(
    override val clientId: String,
    val powerConnectionId: String,
    val evChargingStationClient: EvChargingStationClient,
    val chargingState: ChargingState,
    val chargingStateChangedAt: Long,
    override val loadSharingPriority: LoadSharingPriority,
    val chargeRateLimit: Int,
    override val usesThreePhase: Boolean,

    val dataResponse: DataResponse,
    val pwmDutyCyclePercent: Int,

    val measuredCurrentPeakAt: Long?,
    val measuredCurrentInAmpsAvg: Double?,

    val reasonChargingUnavailable: String?,
    private val chargingEndingAmpDelta: Int
) : LoadSharable {

    override fun isCharging(): Boolean = chargingState == Charging
    override fun isChargingOrChargingRequested(): Boolean = isCharging() || chargingState == ChargingRequested

    override fun getConfiguredRateAmps() =
        if (chargingState == ChargingRequested || chargingState == Charging) {
            pwmPercentToChargingRate(pwmDutyCyclePercent)
        } else
            error("Not in a state which allows current to flow. state=$chargingState")

    private fun getPossibleRateAmps(): Int = min(dataResponse.proximityPilotAmps.ampValue, chargeRateLimit)

    override fun wantsMore(timestamp: Long): Boolean {
        if (chargingState !in listOf(Charging, ChargingRequested)) {
            return false
        }

        if (getPossibleRateAmps() <= getConfiguredRateAmps()) {
            return false
        }

        if (isCharging() && measuredCurrentInAmpsAvg != null && measuredCurrentPeakAt != null && timestamp - measuredCurrentPeakAt > 60 * 1000) {
            // already reached peak power draw
            return getConfiguredRateAmps() < (measuredCurrentInAmpsAvg.roundToInt() + chargingEndingAmpDelta)
        }

        return true
    }

    override fun setNoCapacityAvailable(timestamp: Long) = if (chargingState != ConnectedChargingUnavailable)
        changeState(ConnectedChargingUnavailable, timestamp, "No capacity available")
    else
        this

    override fun adjustMaxChargeCurrent(amps: Int, timestamp: Long) =
        when {
            amps < 1 && chargingState == ChargingRequested -> this
            amps < 1 -> copy(pwmDutyCyclePercent = PWM_OFF)
            amps >= LOWEST_CHARGE_RATE && chargingState == ChargingRequested -> copy(
                chargingState = Charging,
                chargingStateChangedAt = timestamp,
                pwmDutyCyclePercent = chargeRateToPwmPercent(amps),
                measuredCurrentInAmpsAvg = null,
                measuredCurrentPeakAt = null
            )

            amps >= LOWEST_CHARGE_RATE -> copy(pwmDutyCyclePercent = chargeRateToPwmPercent(amps))
            else -> error("Impossible this=$this amps=$amps")
        }

    fun updateData(evChargingStationClient: EvChargingStationClient, dataResponse: DataResponse) =
        if (dataResponse.pwmPercent > this.dataResponse.pwmPercent) copy(
            evChargingStationClient = evChargingStationClient,
            dataResponse = dataResponse,
            measuredCurrentPeakAt = null,
            measuredCurrentInAmpsAvg = null
        ) else copy(
            evChargingStationClient = evChargingStationClient,
            dataResponse = dataResponse
        )

    fun setReasonChargingUnavailable(reasonChargingUnavailable: String?) = copy(
        reasonChargingUnavailable = reasonChargingUnavailable
    )

    fun changeState(
        chargingState: ChargingState,
        timestamp: Long,
        reasonChargingUnavailable: String? = null,
        fake: Boolean = false
    ): InternalState {
        if (chargingState == ConnectedChargingUnavailable) {
            check(reasonChargingUnavailable != null)
        }

        return if (this.chargingState == chargingState) {
            this.copy(reasonChargingUnavailable = reasonChargingUnavailable)
        } else {
            if (!fake) {
                EvChargingService.logger.info { "StateChange for ${this.clientId}:  ${this.chargingState} -> $chargingState" }
            }
            this.copy(
                pwmDutyCyclePercent = PWM_OFF, // will re-adjust by LoadSharing
                chargingState = chargingState,
                chargingStateChangedAt = timestamp,
                reasonChargingUnavailable = if (chargingState == ConnectedChargingUnavailable || chargingState == StoppingCharging) reasonChargingUnavailable else null,
                measuredCurrentInAmpsAvg = null,
                measuredCurrentPeakAt = null
            ).let {
                if (chargingState == Unconnected) {
                    it.copy(usesThreePhase = false)
                } else it
            }
        }
    }

    fun export() = EvChargingStationData(
        chargingState,
        reasonChargingUnavailable,
        chargingStateChangedAt,
        dataResponse.proximityPilotAmps,
        pwmPercentToChargingRate(pwmDutyCyclePercent),
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

fun pwmPercentToChargingRate(pwmPercent: Int) = when (pwmPercent) {
    PWM_OFF -> 0
    else -> (pwmPercent * 6) / 10
}

/*
 * 10% =>  6A
 * 15% => 10A
 * 25% => 16A
 * 40% => 25A
 * 50% => 32A
 *
 * duty cycle = Amps / 0.6
 *
 * https://www.ti.com/lit/ug/tidub87/tidub87.pdf
 */
fun chargeRateToPwmPercent(amps: Int) = if (amps == 0) PWM_OFF else ceil(((amps * 10).toDouble() / 6)).roundToInt()

fun desiredPwmPercent(chargingState: ChargingState, pwmDutyCyclePercent: Int) = when (chargingState) {
    Charging -> pwmDutyCyclePercent
    StoppingCharging -> PWM_NO_CHARGING
    ConnectedChargingAvailable, ChargingRequested -> 10
    else -> 100
}

fun pwmToChargingState(pwmDutyCyclePercent: Int) = when (pwmDutyCyclePercent) {
    PWM_OFF -> ConnectedChargingUnavailable
    else -> Charging
}