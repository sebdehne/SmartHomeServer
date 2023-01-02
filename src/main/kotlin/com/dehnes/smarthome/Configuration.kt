package com.dehnes.smarthome

import com.dehnes.smarthome.config.ConfigService
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
import com.dehnes.smarthome.victron.DalyBmsDataLogger
import com.dehnes.smarthome.victron.VictronEssProcess
import com.dehnes.smarthome.victron.VictronService
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.util.concurrent.Executors
import kotlin.reflect.KClass

fun objectMapper() = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

class Configuration {
    private var beans = mutableMapOf<KClass<*>, Any>()

    fun init() {

        val executorService = Executors.newCachedThreadPool()
        val objectMapper = objectMapper()
        val configService = ConfigService(objectMapper)

        val userSettingsService = UserSettingsService(configService)

        val priceSource = HvakosterstrommenClient(objectMapper)

        val influxDBClient = InfluxDBClient(configService)
        val energyConsumptionService = EnergyConsumptionService(influxDBClient)

        val clock = Clock.system(DateTimeUtils.zoneId)

        val energyPriceService = EnergyPriceService(
            objectMapper,
            priceSource,
            influxDBClient,
            executorService,
            configService
        )
        energyPriceService.start()

        val hanPortService = HanPortService(
            "192.168.1.1",
            23000,
            executorService,
            influxDBClient,
            energyPriceService,
            configService
        )
        hanPortService.start()

        val evChargingStationConnection = EvChargingStationConnection(
            9091,
            executorService,
            configService,
            influxDBClient,
            clock
        )
        evChargingStationConnection.start()

        val victronHost = "192.168.1.18"
        val victronService = VictronService(
            victronHost,
            objectMapper,
            executorService,
            configService,
            influxDBClient,
            energyConsumptionService
        )

        val evChargingService = EvChargingService(
            evChargingStationConnection,
            executorService,
            energyPriceService,
            configService,
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

        val loRaConnection = LoRaConnection(configService, executorService, AES265GCM(configService), clock)
        loRaConnection.start()

        val loRaSensorBoardService = EnvironmentSensorService(
            loRaConnection,
            clock,
            executorService,
            configService,
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
            configService,
            influxDBClient,
            energyPriceService,
            clock,
            victronService,
            energyConsumptionService
        )
        heaterService.start()

        val videoBrowser = VideoBrowser()

        val dalyBmsDataLogger = DalyBmsDataLogger(influxDBClient, objectMapper, victronHost, executorService)
        dalyBmsDataLogger.apply {
            reconnect()
            resubscribe()
        }


        val victronEssProcess = VictronEssProcess(
            executorService,
            victronService,
            configService,
            energyPriceService,
            dalyBmsDataLogger
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
        beans[EnergyConsumptionService::class] = energyConsumptionService
    }

    fun <T> getBean(klass: KClass<*>): T {
        return (beans[klass] ?: error("No such bean for $klass")) as T
    }

}