package com.dehnes.smarthome.zwave

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.objectMapper
import com.dehnes.smarthome.users.UserSettingsService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.Executors

class StairsHeatingServiceTest {

    @Disabled
    @Test
    fun test() {
        val objectMapper = objectMapper()
        val executorService = Executors.newCachedThreadPool()
        val zWaveMqttClient = ZWaveMqttClient(
            "192.168.1.18",
            objectMapper,
            executorService,
            Duration.ofSeconds(120)
        )

        val configService = ConfigService(objectMapper)
        val influxDBClient = InfluxDBClient(configService)
        val quickStatsService = mockk<QuickStatsService>()
        every {
            quickStatsService.listeners
        } returns mutableMapOf()

        val stairsHeatingService = StairsHeatingService(
            zWaveMqttClient,
            Clock.systemDefaultZone(),
            influxDBClient,
            quickStatsService,
            configService,
            executorService,
            UserSettingsService(configService)
        )

        stairsHeatingService.init()

        Thread.sleep(3000 * 1000)


    }
}