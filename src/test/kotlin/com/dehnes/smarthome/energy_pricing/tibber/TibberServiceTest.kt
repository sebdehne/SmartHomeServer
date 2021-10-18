package com.dehnes.smarthome.energy_pricing.tibber

import com.dehnes.smarthome.Configuration
import com.dehnes.smarthome.utils.PersistenceService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Executors

@Disabled
internal class TibberServiceTest {

    @Test
    fun test() {
        val persistenceService = mockk<PersistenceService>()
        every {
            persistenceService.get("tibberAuthBearer", any())
        } returns "<insert-token>"

        var time = Instant.parse("2021-10-18T06:34:00.000Z")
        val clock = mockk<Clock>()
        every {
            clock.instant()
        } answers {
            time
        }

        val tibberService = TibberService(
            clock,
            Configuration().objectMapper(),
            persistenceService,
            mockk(relaxed = true),
            Executors.newSingleThreadExecutor()
        )

        (0..10).map { it * 10 }.forEach { p ->
            println("p=$p" + tibberService.mustWaitUntilV2(p))
        }
    }
}