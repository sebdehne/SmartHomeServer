package com.dehnes.smarthome

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.HvakosterstrommenClient
import com.dehnes.smarthome.enery.EnergyService
import com.dehnes.smarthome.han.GridMonitor
import com.dehnes.smarthome.environment_sensors.EnvironmentSensorService
import com.dehnes.smarthome.ev_charging.*
import com.dehnes.smarthome.garage_door.GarageController
import com.dehnes.smarthome.han.HanPortService
import com.dehnes.smarthome.heating.UnderFloorHeaterService
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.utils.AES265GCM
import com.dehnes.smarthome.utils.DateTimeUtils
import com.dehnes.smarthome.utils.PersistenceService
import com.dehnes.smarthome.victron.VictronService
import com.fasterxml.jackson.annotation.JsonInclude
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
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun init() {

        val executorService = Executors.newCachedThreadPool()
        val objectMapper = objectMapper()
        val persistenceService = PersistenceService(objectMapper)

        //val priceSource = TibberPriceClient(objectMapper, persistenceService)
        val priceSource = HvakosterstrommenClient(objectMapper)

        val influxDBClient = InfluxDBClient(persistenceService, objectMapper)

        val clock = Clock.system(DateTimeUtils.zoneId)

        val energyPriceService = EnergyPriceService(
            clock,
            objectMapper,
            priceSource,
            influxDBClient,
            executorService
        )
        energyPriceService.start()

        val gridMonitor = GridMonitor()

        val energyService = EnergyService()
        energyService.addUnit(gridMonitor)


        val hanPortService = HanPortService(
            "192.168.1.1",
            23000,
            executorService,
            influxDBClient,
            energyPriceService,
            persistenceService
        )
        hanPortService.listeners.add { gridMonitor.onNewHanData(it) }
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

        EvChargingStationEnergyServices(
            evChargingStationConnection,
            energyService
        )

        val evChargingService = EvChargingService(
            evChargingStationConnection,
            executorService,
            energyPriceService,
            persistenceService,
            clock,
            mapOf(
                PriorityLoadSharing::class.java.simpleName to PriorityLoadSharing(clock)
            ),
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
            energyPriceService,
            clock,
            energyService
        )
        heaterService.start()

        val videoBrowser = VideoBrowser()

        val quickStatsService = QuickStatsService(influxDBClient, hanPortService, executorService)

        val victronService = VictronService(
            "192.168.1.18",
            objectMapper,
            executorService
        )

        beans[UnderFloorHeaterService::class] = heaterService
        beans[GarageController::class] = garageDoorService
        beans[ObjectMapper::class] = objectMapper
        beans[EvChargingStationConnection::class] = evChargingStationConnection
        beans[FirmwareUploadService::class] = firmwareUploadService
        beans[EvChargingService::class] = evChargingService
        beans[EnvironmentSensorService::class] = loRaSensorBoardService
        beans[VideoBrowser::class] = videoBrowser
        beans[QuickStatsService::class] = quickStatsService
        beans[VictronService::class] = victronService
    }

    fun <T> getBean(klass: KClass<*>): T {
        return (beans[klass] ?: error("No such bean for $klass")) as T
    }

}