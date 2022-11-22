package com.dehnes.smarthome

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.HvakosterstrommenClient
import com.dehnes.smarthome.environment_sensors.EnvironmentSensorService
import com.dehnes.smarthome.ev_charging.EvChargingService
import com.dehnes.smarthome.ev_charging.EvChargingStationConnection
import com.dehnes.smarthome.ev_charging.FirmwareUploadService
import com.dehnes.smarthome.ev_charging.PriorityLoadSharing
import com.dehnes.smarthome.garage_door.GarageController
import com.dehnes.smarthome.han.HanPortService
import com.dehnes.smarthome.heating.UnderFloorHeaterService
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.AES265GCM
import com.dehnes.smarthome.utils.DateTimeUtils
import com.dehnes.smarthome.utils.PersistenceService
import com.dehnes.smarthome.victron.VictronEssProcess
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

        val userSettingsService = UserSettingsService(persistenceService)

        //val priceSource = TibberPriceClient(objectMapper, persistenceService)
        val priceSource = HvakosterstrommenClient(objectMapper)

        val influxDBClient = InfluxDBClient(persistenceService, objectMapper)
        val energyConsumptionService = EnergyConsumptionService(influxDBClient)

        val clock = Clock.system(DateTimeUtils.zoneId)

        val energyPriceService = EnergyPriceService(
            objectMapper,
            priceSource,
            influxDBClient,
            executorService,
            persistenceService
        )
        energyPriceService.start()

        val hanPortService = HanPortService(
            "192.168.1.1",
            23000,
            executorService,
            influxDBClient,
            energyPriceService,
            persistenceService
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

        val victronService = VictronService(
            "192.168.1.18",
            objectMapper,
            executorService,
            persistenceService,
            influxDBClient,
            energyConsumptionService
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
            victronService,
            userSettingsService,
            energyConsumptionService
        )
        evChargingService.start()

        val firmwareUploadService = FirmwareUploadService(evChargingStationConnection, userSettingsService)

        val loRaConnection = LoRaConnection(persistenceService, executorService, AES265GCM(persistenceService), clock)
        loRaConnection.start()

        val loRaSensorBoardService = EnvironmentSensorService(
            loRaConnection,
            clock,
            executorService,
            persistenceService,
            influxDBClient
        )

        val garageDoorService = GarageController(
            loRaConnection,
            clock,
            influxDBClient,
            executorService,
            userSettingsService
        )
        garageDoorService.start()

        val heaterService = UnderFloorHeaterService(
            loRaConnection,
            executorService,
            persistenceService,
            influxDBClient,
            energyPriceService,
            clock,
            victronService,
            energyConsumptionService
        )
        heaterService.start()

        val videoBrowser = VideoBrowser()


        val victronEssProcess = VictronEssProcess(
            executorService,
            victronService,
            persistenceService,
            energyPriceService
        )
        victronEssProcess.start()

        val quickStatsService = QuickStatsService(influxDBClient, hanPortService, executorService, victronService)

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
        beans[VictronEssProcess::class] = victronEssProcess
        beans[EnergyPriceService::class] = energyPriceService
        beans[UserSettingsService::class] = userSettingsService
    }

    fun <T> getBean(klass: KClass<*>): T {
        return (beans[klass] ?: error("No such bean for $klass")) as T
    }

}