package com.dehnes.smarthome.energy_pricing.tibber

import com.dehnes.smarthome.Configuration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.Executors

@Disabled
internal class TibberServiceTest {

    @Test
    fun test() {
        val tibberService = TibberService(
            Clock.system(ZoneId.of("Europe/Oslo")),
            Configuration().objectMapper(),
            HvakosterstrommenClient(jacksonObjectMapper()),
            mockk(relaxed = true),
            Executors.newSingleThreadExecutor()
        )

        (0..10).map { it * 10 }.forEach { p ->
            println("p=$p " + tibberService.mustWaitUntilV2(p))
        }
    }
}