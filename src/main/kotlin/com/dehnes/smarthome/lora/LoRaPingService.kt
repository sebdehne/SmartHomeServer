package com.dehnes.smarthome.lora

import com.dehnes.smarthome.ev_charging.toHexString
import mu.KotlinLogging

class LoRaPingService(
    loRaConnection: LoRaConnection
) {

    private val logger = KotlinLogging.logger { }

    init {
        loRaConnection.listeners.add { packet ->
            if (packet.type == LoRaPacketType.REQUEST_PING) {
                logger.info { "Ping from ${packet.from} with payload=${packet.payload.toHexString()}" }

                loRaConnection.send(packet.keyId, packet.from, LoRaPacketType.RESPONSE_PONG, packet.payload) {
                    if (!it) {
                        logger.info { "Could not send pong response" }
                    }
                }
                true
            } else {
                false
            }
        }
    }
}