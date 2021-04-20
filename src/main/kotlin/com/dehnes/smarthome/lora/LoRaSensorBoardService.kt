package com.dehnes.smarthome.lora

import com.dehnes.smarthome.ev_charging.toHexString
import mu.KotlinLogging

class LoRaSensorBoardService(
    loRaConnection: LoRaConnection
) {

    private val logger = KotlinLogging.logger { }

    init {
        loRaConnection.listeners.add { packet ->
            if (packet.type == LoRaPacketType.SENSOR_DATA_REQUEST) {
                logger.info { "Sensor data from ${packet.from} with payload=${packet.payload.toHexString()}" }

                true
            } else {
                false
            }
        }
    }
}