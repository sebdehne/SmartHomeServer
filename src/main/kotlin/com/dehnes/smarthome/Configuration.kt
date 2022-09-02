package com.dehnes.smarthome

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.energy_pricing.tibber.TibberService
import com.dehnes.smarthome.environment_sensors.EnvironmentSensorService
import com.dehnes.smarthome.ev_charging.EvChargingService
import com.dehnes.smarthome.ev_charging.EvChargingStationConnection
import com.dehnes.smarthome.ev_charging.FirmwareUploadService
import com.dehnes.smarthome.ev_charging.PriorityLoadSharing
import com.dehnes.smarthome.garage_door.GarageController
import com.dehnes.smarthome.han.HanPortService
import com.dehnes.smarthome.heating.UnderFloorHeaterService
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.utils.AES265GCM
import com.dehnes.smarthome.utils.DateTimeUtils
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class Configuration {
    private var beans = mutableMapOf<KClass<*>, Any>()

    fun objectMapper() = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun init() {

        val executorService = Executors.newCachedThreadPool()
        val objectMapper = objectMapper()

        val persistenceService = PersistenceService(objectMapper)
        val influxDBClient = InfluxDBClient(persistenceService, objectMapper)

        val clock = Clock.system(DateTimeUtils.zoneId)

        val tibberService = TibberService(
            clock,
            objectMapper,
            persistenceService,
            influxDBClient,
            executorService
        )
        tibberService.start()

        val hanPortService = HanPortService(
            "192.168.1.1",
            23000,
            executorService,
            influxDBClient,
            tibberService
        )
        hanPortService.start()

        val evChargingStationConnection = EvChargingStationConnection(
            9091,
            executorService,
            persistenceService,
            influxDBClient,
            objectMapper,
            clock
        )
        evChargingStationConnection.start()

        val evChargingService = EvChargingService(
            evChargingStationConnection,
            executorService,
            tibberService,
            persistenceService,
            clock,
            mapOf(
                PriorityLoadSharing::class.java.simpleName to PriorityLoadSharing(clock)
            )
        )
        evChargingService.start()

        val firmwareUploadService = FirmwareUploadService(evChargingStationConnection)

        val loRaConnection = LoRaConnection(persistenceService, executorService, AES265GCM(persistenceService), clock)
        loRaConnection.start()

        val loRaSensorBoardService = EnvironmentSensorService(
            loRaConnection,
            clock,
            executorService,
            persistenceService,
            influxDBClient
        )

        val garageDoorService = GarageController(loRaConnection, clock, influxDBClient, executorService)
        garageDoorService.start()

        val heaterService = UnderFloorHeaterService(
            loRaConnection,
            executorService,
            persistenceService,
            influxDBClient,
            tibberService,
            clock
        )
        heaterService.start()

        val videoBrowser = VideoBrowser()

        val quickStatsService = QuickStatsService(influxDBClient, hanPortService, executorService)

        beans[UnderFloorHeaterService::class] = heaterService
        beans[GarageController::class] = garageDoorService
        beans[ObjectMapper::class] = objectMapper
        beans[EvChargingStationConnection::class] = evChargingStationConnection
        beans[FirmwareUploadService::class] = firmwareUploadService
        beans[EvChargingService::class] = evChargingService
        beans[EnvironmentSensorService::class] = loRaSensorBoardService
        beans[VideoBrowser::class] = videoBrowser
        beans[QuickStatsService::class] = quickStatsService
    }

    fun <T> getBean(klass: KClass<*>): T {
        return (beans[klass] ?: error("No such bean for $klass")) as T
    }

}