package com.dehnes.smarthome

import com.dehnes.smarthome.external.InfluxDBClient
import com.dehnes.smarthome.external.SerialConnection
import com.dehnes.smarthome.external.TibberPriceClient
import com.dehnes.smarthome.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class Configuration {
    private var beans = mutableMapOf<KClass<*>, Any>()

    fun init() {

        val executorService = Executors.newCachedThreadPool()
        val objectMapper = jacksonObjectMapper()

        val influxDBClient = InfluxDBClient(objectMapper, System.getProperty("DST_HOST"))
        val persistenceService = PersistenceService()

        val serialConnection = SerialConnection(executorService, System.getProperty("DST_HOST"))
        serialConnection.start()

        val garageDoorService = GarageDoorService(serialConnection, influxDBClient)
        val chipCap2SensorService = ChipCap2SensorService(influxDBClient)

        val tibberService = TibberService(
            Clock.systemDefaultZone(),
            TibberPriceClient(objectMapper, persistenceService),
            influxDBClient,
            executorService
        )
        tibberService.start()

        val heaterService = UnderFloorHeaterService(
            serialConnection,
            executorService,
            persistenceService,
            influxDBClient,
            tibberService
        )
        heaterService.start()
        serialConnection.listeners.add(heaterService::onRfMessage)
        serialConnection.listeners.add(garageDoorService::handleIncoming)
        serialConnection.listeners.add(chipCap2SensorService::handleIncoming)

        beans[SerialConnection::class] = serialConnection
        beans[UnderFloorHeaterService::class] = heaterService
        beans[GarageDoorService::class] = garageDoorService
        beans[ObjectMapper::class] = objectMapper
    }

    fun <T> getBean(klass: KClass<*>): T {
        return (beans[klass] ?: error("No such bean for $klass")) as T
    }

}