package com.dehnes.smarthome

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.energy_pricing.tibber.TibberService
import com.dehnes.smarthome.ev_charging.EvChargingStationConnection
import com.dehnes.smarthome.ev_charging.EvChargingService
import com.dehnes.smarthome.ev_charging.FirmwareUploadService
import com.dehnes.smarthome.ev_charging.PriorityLoadSharing
import com.dehnes.smarthome.garage_door.GarageDoorService
import com.dehnes.smarthome.heating.UnderFloorHeaterService
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.lora.LoRaSensorBoardService
import com.dehnes.smarthome.rf433.Rf433Client
import com.dehnes.smarthome.room_sensors.ChipCap2SensorService
import com.dehnes.smarthome.utils.AES265GCM
import com.dehnes.smarthome.utils.PersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.ZoneId
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

        val influxDBClient = InfluxDBClient(objectMapper, System.getProperty("DST_HOST"))
        val persistenceService = PersistenceService()

        val serialConnection = Rf433Client(executorService, System.getProperty("DST_HOST"))
        serialConnection.start()

        val garageDoorService = GarageDoorService(serialConnection, influxDBClient)
        val chipCap2SensorService = ChipCap2SensorService(influxDBClient)

        val clock = Clock.system(ZoneId.of("Europe/Oslo"))

        val tibberService = TibberService(
            clock,
            objectMapper,
            persistenceService,
            influxDBClient,
            executorService
        )
        tibberService.start()

        val heaterService = UnderFloorHeaterService(
            serialConnection,
            executorService,
            persistenceService,
            influxDBClient,
            tibberService,
            clock
        )
        heaterService.start()

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
                PriorityLoadSharing::class.java.simpleName to PriorityLoadSharing(persistenceService, clock)
            )
        )
        evChargingService.start()

        val firmwareUploadService = FirmwareUploadService(evChargingStationConnection)

        serialConnection.listeners.add(heaterService::onRfMessage)
        serialConnection.listeners.add(garageDoorService::handleIncoming)
        serialConnection.listeners.add(chipCap2SensorService::handleIncoming)

        val loRaConnection = LoRaConnection(persistenceService, executorService, AES265GCM(persistenceService), clock)
        loRaConnection.start()

        LoRaSensorBoardService(loRaConnection, clock, persistenceService)

        beans[Rf433Client::class] = serialConnection
        beans[UnderFloorHeaterService::class] = heaterService
        beans[GarageDoorService::class] = garageDoorService
        beans[ObjectMapper::class] = objectMapper
        beans[EvChargingStationConnection::class] = evChargingStationConnection
        beans[FirmwareUploadService::class] = firmwareUploadService
        beans[EvChargingService::class] = evChargingService
    }

    fun <T> getBean(klass: KClass<*>): T {
        return (beans[klass] ?: error("No such bean for $klass")) as T
    }

}