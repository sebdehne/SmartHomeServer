package com.dehnes.smarthome.lora

import com.dehnes.smarthome.ev_charging.toHexString
import mu.KotlinLogging

class LoRaPingService(
    loRaConnection: LoRaConnection
) {

    private val logger = KotlinLogging.logger { }

    init {
        loRaConnection.listeners.add { packet ->
            if (packet.getType() == LoRaPacketType.REQUEST_PING) {
                logger.info { "Ping from ${packet.getFromAddr()} with payload=${packet.getPayload().toHexString()}" }

                loRaConnection.send(packet.getFromAddr(), LoRaPacketType.RESPONSE_PONG, packet.getPayload()) {
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