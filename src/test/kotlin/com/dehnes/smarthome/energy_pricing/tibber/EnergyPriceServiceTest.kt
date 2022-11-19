package com.dehnes.smarthome.energy_pricing.tibber

import com.dehnes.smarthome.Configuration
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.HvakosterstrommenClient
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

@Disabled
internal class EnergyPriceServiceTest {

    @Test
    fun test() {
        val objectMapper = Configuration().objectMapper()
        val energyPriceService = EnergyPriceService(
            objectMapper,
            HvakosterstrommenClient(jacksonObjectMapper()),
            mockk(relaxed = true),
            Executors.newSingleThreadExecutor(),
            PersistenceService(objectMapper)
        )


    }
}