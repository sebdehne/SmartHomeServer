package com.dehnes.smarthome.service.ev_charging_station

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.external.ev_charing_station.DataResponse
import com.dehnes.smarthome.external.ev_charing_station.EVChargingStationConnection
import com.dehnes.smarthome.external.ev_charing_station.EventType
import com.dehnes.smarthome.service.AbstractProcess
import com.dehnes.smarthome.service.PersistenceService
import com.dehnes.smarthome.service.TibberService
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class EvChargingService(
    private val eVChargingStationConnection: EVChargingStationConnection,
    private val executorService: ExecutorService,
    private val tibberService: TibberService,
    private val persistenceService: PersistenceService
) : AbstractProcess(
    executorService,
    5
) {
    val listeners = ConcurrentHashMap<String, (EvChargingEvent) -> Unit>()
    private val currentData = ConcurrentHashMap<String, InternalState>()
    private val chargingStates = listOf(ChargingStationState.StatusC, ChargingStationState.StatusD)

    //
    private val chargeRateDeclineDeltaMinAmp = persistenceService.get("chargeRateDeclineDeltaMinAmp", "2")!!.toInt()

    private val logger = KotlinLogging.logger { }
    override fun logger() = logger

    override fun start() {
        super.start()

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
                    val dataResponse = event.clientData!!

                    currentData.compute(event.evChargingStationClient.clientId) { _, u ->
                        val previousCharging = u?.dataResponse?.chargingState in chargingStates
                        val chargingNow = dataResponse.chargingState in chargingStates
                        val currentChargeAmp = dataResponse.measuredCurrentInAmp()

                        var peak: Int? = null
                        var peakTime: Long? = null
                        var measuredChargeRateStartedToDeclineAt: Long? = null

                        if (chargingNow) {
                            if (u == null) {
                                peak = currentChargeAmp
                                peakTime = System.currentTimeMillis()
                            } else {
                                if (!previousCharging) {
                                    peak = u.dataResponse.measuredCurrentInAmp()
                                    peakTime = System.currentTimeMillis()
                                } else {
                                    // continuing
                                    peak = u.measuredChargeRatePeakValue!!
                                    peakTime = u.measuredChargeRatePeakTime
                                    if (currentChargeAmp > peak) {
                                        // increasing
                                        peak = currentChargeAmp
                                        peakTime = System.currentTimeMillis()
                                        measuredChargeRateStartedToDeclineAt = null
                                    } else {
                                        // declining
                                        if (measuredChargeRateStartedToDeclineAt == null && peak - currentChargeAmp > chargeRateDeclineDeltaMinAmp) {
                                            // break point reached
                                            measuredChargeRateStartedToDeclineAt = System.currentTimeMillis()
                                        } else {
                                            // peak should follow downwards
                                            peak = currentChargeAmp
                                            peakTime = System.currentTimeMillis()
                                        }
                                    }
                                }
                            }
                        }

                        InternalState(
                            event.evChargingStationClient,
                            dataResponse,
                            if (chargingNow) {
                                u?.utcTimestampStartedCharging ?: System.currentTimeMillis()
                            } else {
                                null
                            },
                            peak,
                            peakTime,
                            measuredChargeRateStartedToDeclineAt
                        )
                    }
                    executorService.submit {
                        listeners.forEach { (_, fn) ->
                            fn(
                                EvChargingEvent(
                                    EvChargingEventType.data, event.evChargingStationClient, dataResponse.map()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun getConnectedClients() = eVChargingStationConnection.getConnectedClients()
    fun getData(clientId: String) = currentData[clientId]?.dataResponse

    fun updateMode(clientId: String, evChargingMode: EvChargingMode) {
        persistenceService["EvChargingMode.$clientId"] = evChargingMode.name
    }

    fun getMode(clientId: String) =
        persistenceService.get("EvChargingMode.$clientId", EvChargingMode.ChargeDuringCheapHours.name)!!
            .let { EvChargingMode.valueOf(it) }

    override fun tickLocked(): Boolean {
        val evStationsData = currentData.values.toList()

        evStationsData.map { it.evChargingStationClient.powerConnectionId }.distinct().forEach { powerConnectionId ->

            val maxAmps = persistenceService.get("PowerConnectionMaxAmps.$powerConnectionId", "32")!!.toInt()
            val numberOfHoursRequired =
                persistenceService.get("PowerConnectionCheapestHours.$powerConnectionId", "4")!!.toInt()

            val mappings = mutableMapOf<String, Int>()

            val connectedStations = evStationsData
                .filter { it.evChargingStationClient.powerConnectionId == powerConnectionId }

            val stationsCanCharge = connectedStations.filter {
                val mode = getMode(it.evChargingStationClient.clientId)
                mode == EvChargingMode.ON || (mode == EvChargingMode.ChargeDuringCheapHours && tibberService.isEnergyPriceOK(
                    numberOfHoursRequired
                ))
            }

            val stationsOff =
                connectedStations.filter {
                    val mode = getMode(it.evChargingStationClient.clientId)
                    mode == EvChargingMode.OFF || (mode == EvChargingMode.ChargeDuringCheapHours && !tibberService.isEnergyPriceOK(
                        numberOfHoursRequired
                    ))
                }

            stationsOff.forEach {
                mappings[it.evChargingStationClient.clientId] = 0
            }

            if (stationsCanCharge.isNotEmpty()) {

                val (stationsWithAvailableCapacity, others) = stationsCanCharge.partition { internalState ->
                    internalState.measuredChargeRateStartedToDeclineAt?.let {
                        (System.currentTimeMillis() - it) > (5 * 60 * 1000)
                    } ?: false
                }

                val usedCapacity = stationsWithAvailableCapacity.map { it.dataResponse.measuredCurrentInAmp() }.sum()
                val availableCapacity = maxAmps - usedCapacity

                stationsWithAvailableCapacity.forEach {
                    mappings[it.evChargingStationClient.clientId] = it.dataResponse.measuredCurrentInAmp()
                }
                others.forEach {
                    mappings[it.evChargingStationClient.clientId] = availableCapacity / others.size
                }
            }

            val (decrements, incrementsOrSame) = mappings.entries.partition { (clientId, chargeRate) ->
                val internalState = stationsCanCharge.first { it.evChargingStationClient.clientId == clientId }
                chargeRate < internalState.dataResponse.chargeCurrentAmps
            }

            // reduce charing rate before increasing others
            if (decrements.isNotEmpty()) {
                decrements.forEach { (clientId, chargeRate) ->
                    val internalState = stationsCanCharge.first { it.evChargingStationClient.clientId == clientId }
                    val result = eVChargingStationConnection.sendMaxChargeRate(clientId, chargeRate)
                    logger.info { "Decreasing charge rate for $clientId from ${internalState.dataResponse.chargeCurrentAmps} to $chargeRate. result=$result" }
                }
            } else {
                incrementsOrSame.forEach { (clientId, chargeRate) ->
                    val internalState = stationsCanCharge.first { it.evChargingStationClient.clientId == clientId }
                    if (internalState.dataResponse.chargeCurrentAmps != chargeRate) {
                        val result = eVChargingStationConnection.sendMaxChargeRate(clientId, chargeRate)
                        logger.info { "Increasing charge rate for $clientId from ${internalState.dataResponse.chargeCurrentAmps} to $chargeRate. result=$result" }
                    }
                }
            }

        }

        return true
    }

}

data class InternalState(
    val evChargingStationClient: EvChargingStationClient,
    val dataResponse: DataResponse,
    val utcTimestampStartedCharging: Long?,
    val measuredChargeRatePeakValue: Int?,
    val measuredChargeRatePeakTime: Long?,
    val measuredChargeRateStartedToDeclineAt: Long?
)