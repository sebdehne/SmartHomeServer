package com.dehnes.smarthome.service.ev_charging_station

import com.dehnes.smarthome.api.dtos.EvChargingMode
import com.dehnes.smarthome.api.dtos.EvChargingStationClient
import com.dehnes.smarthome.api.dtos.ProximityPilotAmps
import com.dehnes.smarthome.utils.PersistenceService
import com.dehnes.smarthome.energy_pricing.tibber.TibberService
import com.dehnes.smarthome.ev_charging.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class EvChargingServiceTest {

    val timeHolder = AtomicReference(Instant.now())
    val isEnergyPriceOK = AtomicBoolean(true)
    val tibberService = mockk<TibberService>()
    val executorService = mockk<ExecutorService>()
    val persistenceService = mockk<PersistenceService>()
    val eVChargingStationConnection = mockk<EvChargingStationConnection>()
    val currentModes = mutableMapOf<String, EvChargingMode>()
    val clockMock = mockk<Clock>()

    val allChargingsStations = mutableMapOf<String, TestChargingStation>()
    val onlineChargingStations = mutableSetOf<String>()

    init {
        every {
            tibberService.isEnergyPriceOK(any())
        } answers {
            isEnergyPriceOK.get()
        }

        val slot = slot<Runnable>()
        every {
            executorService.submit(any())
        } answers {
            slot.captured.run()
            null
        }

        val keySlot = slot<String>()
        val defaultSlot = slot<String>()
        every {
            persistenceService.get(
                capture(keySlot),
                capture(defaultSlot)
            )
        } answers {
            val key = keySlot.captured
            if (key.startsWith("EvChargingService.client.mode.")) {
                val clientId = key.split(".")[3]
                if (clientId in currentModes) {
                    currentModes[clientId]!!.name
                } else {
                    defaultSlot.captured
                }
            } else {
                defaultSlot.captured
            }
        }

        val clientIdSlot = slot<String>()
        val contactorSlot = slot<Boolean>()
        every {
            eVChargingStationConnection.setContactorState(
                capture(clientIdSlot),
                capture(contactorSlot)
            )
        } answers {
            if (clientIdSlot.captured in onlineChargingStations) {
                (allChargingsStations[clientIdSlot.captured]
                    ?: error("Missing charging station ${clientIdSlot.captured}")).contactorOn = contactorSlot.captured
                true
            } else
                false
        }

        val pwmSlot = slot<Int>()
        every {
            eVChargingStationConnection.setPwmPercent(
                capture(clientIdSlot),
                capture(pwmSlot)
            )
        } answers {
            if (clientIdSlot.captured in onlineChargingStations) {
                (allChargingsStations[clientIdSlot.captured]
                    ?: error("Missing charging station ${clientIdSlot.captured}")).pwmPercent = pwmSlot.captured
                true
            } else
                false
        }

        every {
            clockMock.millis()
        } answers {
            timeHolder.get().toEpochMilli()
        }
    }

    val evChargingService = EvChargingService(
        eVChargingStationConnection,
        executorService,
        tibberService,
        persistenceService,
        clockMock,
        mapOf(
            PriorityLoadSharing::class.java.simpleName to PriorityLoadSharing(persistenceService)
        )
    )

    @Test
    fun testOnlineAndUnconnected() {

        val s1 = newTestStation("s1")
        val s2 = newTestStation("s2")

        collectDataCycle()

        assertFalse(s1.contactorOn)
        assertEquals(100, s1.pwmPercent)
        assertFalse(s2.contactorOn)
        assertEquals(100, s2.pwmPercent)
    }

    @Test
    @Disabled
    fun testPlayground() {

        val s1 = newTestStation("s1")
        currentModes["s1"] = EvChargingMode.OFF
        s1.phase1Milliamps = -387
        s1.phase2Milliamps = -265
        s1.phase3Milliamps = -666
        s1.pilotVoltage = PilotVoltage.Volt_12
        s1.pwmPercent = 100
        s1.contactorOn = false

        collectDataCycle()

        s1.pilotVoltage = PilotVoltage.Volt_9
        s1.phase1Milliamps = -240
        s1.phase2Milliamps = -106
        s1.phase3Milliamps = -840

        collectDataCycle()
        collectDataCycle()

        currentModes["s1"] = EvChargingMode.ON

        collectDataCycle() // -> ConnectedChargingAvailable

        s1.pwmPercent = 11
        s1.pilotVoltage = PilotVoltage.Volt_6
        s1.phase1Milliamps = -413
        s1.phase2Milliamps = -278
        s1.phase3Milliamps = -872

        collectDataCycle()

        // conactor -> on
        // why resending pwm 11?
        s1.pwmPercent = 51
        s1.contactorOn = true
        s1.phase1Milliamps = -360
        s1.phase2Milliamps = -437
        s1.phase3Milliamps = -919


        collectDataCycle()
        collectDataCycle()

    }

    @Test
    fun testOnlineAndOneStartsChargingGetsAll() {

        val s1 = newTestStation("s1")
        val s2 = newTestStation("s2")

        collectDataCycle()

        // Car connects
        s1.pilotVoltage = PilotVoltage.Volt_9

        collectDataCycle()

        assertFalse(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(LOWEST_MAX_CHARGE_RATE), s1.pwmPercent)
        assertFalse(s2.contactorOn)
        assertEquals(100, s2.pwmPercent)

        // Car ready to charge
        s1.pilotVoltage = PilotVoltage.Volt_6

        collectDataCycle()

        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(32), s1.pwmPercent)
        assertFalse(s2.contactorOn)
        assertEquals(100, s2.pwmPercent)

        // Second car connects
        s2.pilotVoltage = PilotVoltage.Volt_9

        collectDataCycle()

        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(32), s1.pwmPercent)
        assertFalse(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(LOWEST_MAX_CHARGE_RATE), s2.pwmPercent)

        // Second car starts charging
        s2.pilotVoltage = PilotVoltage.Volt_6

        collectDataCycle()

        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(16), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(16), s2.pwmPercent)

        // both reach max current
        s1.setMeasuredCurrent(16)
        s2.setMeasuredCurrent(16)

        collectDataCycle()

        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(16), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(16), s2.pwmPercent)

        // Car 1 rate declining
        s1.setMeasuredCurrent(15)
        s2.setMeasuredCurrent(16)
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(26, s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(26, s2.pwmPercent)

        // Car 1 rate declining below threshold
        s1.setMeasuredCurrent(13)
        s2.setMeasuredCurrent(16)
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(15), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(17), s2.pwmPercent)

        // Car 1 rate declining below threshold
        s1.setMeasuredCurrent(10)
        s2.setMeasuredCurrent(16)
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(12), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(20), s2.pwmPercent)

        // Car 1 rate declining below threshold
        s1.setMeasuredCurrent(1)
        s2.setMeasuredCurrent(16)
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(LOWEST_MAX_CHARGE_RATE), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(26), s2.pwmPercent)

        // Car 1 rate declining below threshold
        s1.setMeasuredCurrent(0)
        s2.setMeasuredCurrent(16)
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(LOWEST_MAX_CHARGE_RATE), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(26), s2.pwmPercent)

        // Car 1 stops
        s1.pilotVoltage = PilotVoltage.Volt_9
        collectDataCycle()
        assertFalse(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(LOWEST_MAX_CHARGE_RATE), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(32), s2.pwmPercent)

        // Car 1 read
        s1.pilotVoltage = PilotVoltage.Volt_6
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(16), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(16), s2.pwmPercent)

        // Car 1 - stopped from app
        currentModes[s1.clientId] = EvChargingMode.OFF
        collectDataCycle()
        assertTrue(s1.contactorOn)
        assertEquals(chargeRateToPwmPercent(LOWEST_MAX_CHARGE_RATE), s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(26), s2.pwmPercent)

        // 5 seconds later ...
        timeHolder.set(timeHolder.get().plusSeconds(5))
        collectDataCycle()
        assertFalse(s1.contactorOn)
        assertEquals(100, s1.pwmPercent)
        assertTrue(s2.contactorOn)
        assertEquals(chargeRateToPwmPercent(32), s2.pwmPercent)

        // car 2 gets a fault
        s2.pilotVoltage = PilotVoltage.Fault
        collectDataCycle()
        assertFalse(s1.contactorOn)
        assertEquals(100, s1.pwmPercent)
        assertFalse(s2.contactorOn)
        assertEquals(100, s2.pwmPercent)

    }

    private fun collectDataCycle() {
        allChargingsStations.forEach { (_, u) ->
            evChargingService.onIncomingDataUpdate(u.evChargingStationClient, u.toData())
        }
    }

    private fun newTestStation(clientId: String, powerConnectionId: String = "conn1") = TestChargingStation(
        clientId,
        powerConnectionId,
        false,
        100,
        PilotVoltage.Volt_12,
        ProximityPilotAmps.Amp32,
        230 * 1000,
        230 * 1000,
        230 * 1000,
        0,
        0,
        0
    ).apply {
        allChargingsStations[clientId] = this
        onlineChargingStations.add(clientId)
    }


}

data class TestChargingStation(
    val clientId: String,
    val powerConnectionId: String,
    var contactorOn: Boolean,
    var pwmPercent: Int,
    var pilotVoltage: PilotVoltage,
    var proximityPilotAmps: ProximityPilotAmps,
    var phase1Millivolts: Int,
    var phase2Millivolts: Int,
    var phase3Millivolts: Int,
    var phase1Milliamps: Int,
    var phase2Milliamps: Int,
    var phase3Milliamps: Int
) {

    fun setMeasuredCurrent(amps: Int) {
        phase1Milliamps = amps * 1000
        phase2Milliamps = amps * 1000
        phase3Milliamps = amps * 1000
    }

    fun toData() = DataResponse(
        contactorOn,
        pwmPercent,
        pilotVoltage,
        proximityPilotAmps,
        phase1Millivolts,
        1,
        phase2Millivolts,
        1,
        phase3Millivolts,
        1,
        phase1Milliamps,
        1,
        phase2Milliamps,
        1,
        phase3Milliamps,
        1,
        -20,
        0,
        0,
        0,
        emptyList()
    )

    val evChargingStationClient = EvChargingStationClient(
        clientId,
        clientId,
        "127.0.0.1",
        2020,
        1,
        powerConnectionId
    )
}