package com.dehnes.smarthome.ev_charging

import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import mu.KotlinLogging
import java.util.*

class FirmwareUploadService(
    private val evChargingStationConnection: EvChargingStationConnection,
    private val userSettingsService: UserSettingsService,
) {
    private val logger = KotlinLogging.logger { }

    fun uploadVersion(user: String?, clientId: String, firmwareBased64Encoded: String): Boolean {
        if (!userSettingsService.canUserWrite(user, UserRole.firmwareUpgrades)) return false

        return try {
            val firmware = Base64.getDecoder().decode(firmwareBased64Encoded)
            evChargingStationConnection.uploadFirmwareAndReboot(clientId, firmware)
        } catch (e: Exception) {
            logger.error("Could not upload to clientId=$clientId", e)
            false
        }
    }

}