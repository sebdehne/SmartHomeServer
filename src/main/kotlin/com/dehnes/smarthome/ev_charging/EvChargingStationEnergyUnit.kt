package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.enery.EnergyService
import com.dehnes.smarthome.enery.EnergyUnit
import com.dehnes.smarthome.enery.Milliwatts
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class EvChargingStationEnergyServices(
    evChargingStationConnection: EvChargingStationConnection,
    energyService: EnergyService
) {

    private val knownClients = ConcurrentHashMap<String, EvChargingStationEnergyUnit>()

    init {
        evChargingStationConnection.listeners[UUID.randomUUID().toString()] = { event ->
            when (event.eventType) {
                EventType.newClientConnection -> energyService.addUnit(
                    View(
                        event.evChargingStationClient.clientId,
                        event.evChargingStationClient.displayName,
                        this
                    )
                )

                EventType.clientData -> {
                    knownClients[event.evChargingStationClient.clientId] = EvChargingStationEnergyUnit(
                        (event.clientData?.phase1Milliamps?.toLong() ?: 0) * 230 * -1,
                        (event.clientData?.phase2Milliamps?.toLong() ?: 0) * 230 * -1,
                        (event.clientData?.phase3Milliamps?.toLong() ?: 0) * 230 * -1,
                    )
                }

                EventType.closedClientConnection -> {
                    knownClients.remove(event.evChargingStationClient.clientId)
                    energyService.removeUnit(event.evChargingStationClient.clientId)
                }
            }
        }
    }

    class View(
        private val clientId: String,
        private val clientName: String,
        private val parent: EvChargingStationEnergyServices,
    ) : EnergyUnit {
        override val id: String
            get() = clientId
        override val name: String
            get() = clientName

        override fun currentPowerL1(): Milliwatts = parent.knownClients[clientId]?.currentPowerL1 ?: 0
        override fun currentPowerL2(): Milliwatts = parent.knownClients[clientId]?.currentPowerL2 ?: 0
        override fun currentPowerL3(): Milliwatts = parent.knownClients[clientId]?.currentPowerL3 ?: 0
    }
}

class EvChargingStationEnergyUnit(
    val currentPowerL1: Milliwatts,
    val currentPowerL2: Milliwatts,
    val currentPowerL3: Milliwatts,
)