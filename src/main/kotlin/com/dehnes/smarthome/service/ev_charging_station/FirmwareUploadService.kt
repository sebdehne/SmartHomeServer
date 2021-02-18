package com.dehnes.smarthome.service.ev_charging_station

import com.dehnes.smarthome.external.EVChargingStationConnection
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.management.ClassLoadingMXBean

class FirmwareUploadService(
    private val evChargingStationConnection: EVChargingStationConnection
) {
    private val logger = KotlinLogging.logger { }

    init {
        logger.info { "Found following firmware versions: " + listVersions() }
    }

    fun listVersions() =
        javaClass.getResourceAsStream("/EvChargingStationFirmware")
            ?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readLines()
            } ?: emptyList()

    fun uploadVersion(clientId: Int, version: String) = try {
        evChargingStationConnection.uploadFirmwareAndReboot(clientId, getVersion(version))
        true
    } catch (e: Exception) {
        logger.error("Could not upload version $version to clientId=$clientId", e)
        false
    }

    private fun getVersion(version: String) =
        javaClass.getResourceAsStream("/EvChargingStationFirmware/$version")
            ?.use { inputStream ->
                inputStream.readAllBytes()
            } ?: error("version $version not found")

}