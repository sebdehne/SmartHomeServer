package com.dehnes.smarthome

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.QuickStatsService
import com.dehnes.smarthome.firewall_router.DnsBlockingService
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.HvakosterstrommenClient
import com.dehnes.smarthome.environment_sensors.EnvironmentSensorService
import com.dehnes.smarthome.ev_charging.EvChargingService
import com.dehnes.smarthome.ev_charging.EvChargingStationConnection
import com.dehnes.smarthome.ev_charging.FirmwareUploadService
import com.dehnes.smarthome.ev_charging.PriorityLoadSharing
import com.dehnes.smarthome.firewall_router.BlockedMacs
import com.dehnes.smarthome.firewall_router.FirewallService
import com.dehnes.smarthome.garage.GarageController
import com.dehnes.smarthome.garage.HoermannE4Controller
import com.dehnes.smarthome.han.HanPortService
import com.dehnes.smarthome.heating.UnderFloorHeaterService
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.AES265GCM
import com.dehnes.smarthome.utils.DateTimeUtils
import com.dehnes.smarthome.victron.DalyBmsDataLogger
import com.dehnes.smarthome.victron.VictronEssProcess
import com.dehnes.smarthome.victron.VictronService
import com.dehnes.smarthome.zwave.StairsHeatingService
import com.dehnes.smarthome.zwave.ZWaveMqttClient
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
    var beans = mutableMapOf<KClass<*>, Any>()

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
            configService,
            userSettingsService
        )
        energyPriceService.start()

        val hanPortService = HanPortService(
            host = "192.168.1.1",
            23000,
            executorService = executorService,
            influxDBClient = influxDBClient,
            energyPriceService = energyPriceService,
            configService = configService
        )
        hanPortService.start()

        val evChargingStationConnection = EvChargingStationConnection(
            port = 9091,
            executorService = executorService,
            configService = configService,
            influxDBClient = influxDBClient,
            clock = clock
        )
        evChargingStationConnection.start()

        val mqttBroker = "192.168.1.18"
        val victronService = VictronService(
            victronHost = mqttBroker,
            objectMapper = objectMapper,
            executorService = executorService,
            configService = configService,
            influxDBClient = influxDBClient,
            energyConsumptionService = energyConsumptionService
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
            influxDBClient,
            userSettingsService
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
            energyConsumptionService,
            userSettingsService
        )
        heaterService.start()

        val videoBrowser = VideoBrowser(userSettingsService)

        val dalyBmsDataLogger = DalyBmsDataLogger(influxDBClient, objectMapper, mqttBroker, executorService, userSettingsService)
        dalyBmsDataLogger.apply {
            reconnect()
            resubscribe()
        }


        val victronEssProcess = VictronEssProcess(
            executorService,
            victronService,
            configService,
            energyPriceService,
            dalyBmsDataLogger,
            userSettingsService
        )
        victronEssProcess.start()

        val quickStatsService = QuickStatsService(influxDBClient, hanPortService, executorService, victronService, dalyBmsDataLogger)

        val zWaveMqttClient = ZWaveMqttClient(
            mqttBroker,
            objectMapper,
            executorService
        )

        val stairsHeatingService = StairsHeatingService(
            zWaveMqttClient,
            clock,
            influxDBClient,
            quickStatsService,
            configService,
            executorService,
            userSettingsService
        )
        stairsHeatingService.init()

        val firewallService = FirewallService()

        val hoermannE4Controller = HoermannE4Controller(
            configService,
            userSettingsService,
            garageDoorService,
            executorService,
        ).apply { this.start() }


        beans[HoermannE4Controller::class] = hoermannE4Controller
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
        beans[DalyBmsDataLogger::class] = dalyBmsDataLogger
        beans[StairsHeatingService::class] = stairsHeatingService
        beans[DnsBlockingService::class] = DnsBlockingService(userSettingsService, configService, firewallService).apply { start() }
        beans[BlockedMacs::class] = BlockedMacs(userSettingsService, firewallService, configService).apply { start() }
        beans[FirewallService::class] = firewallService
    }

    inline fun <reified T> getBean(): T {
        val kClass = T::class
        return (beans[kClass] ?: error("No such bean $kClass")) as T
    }

}