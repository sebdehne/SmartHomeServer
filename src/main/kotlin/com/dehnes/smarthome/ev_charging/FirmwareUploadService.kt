package com.dehnes.smarthome.ev_charging

import mu.KotlinLogging
import java.util.*

class FirmwareUploadService(
    private val evChargingStationConnection: EVChargingStationConnection
) {
    private val logger = KotlinLogging.logger { }

    fun uploadVersion(clientId: String, firmwareBased64Encoded: String) = try {
        val firmware = Base64.getDecoder().decode(firmwareBased64Encoded)
        evChargingStationConnection.uploadFirmwareAndReboot(clientId, firmware)
        true
    } catch (e: Exception) {
        logger.error("Could not upload to clientId=$clientId", e)
        false
    }

}