package com.dehnes.smarthome.config

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.charset.StandardCharsets

class ConfigService(
    private val objectMapper: ObjectMapper,
) {

    private val filenameJson = System.getProperty("STORAGE_FILE_NAME", "properties.json")

    init {
        if (!File(filenameJson).exists()) {
            writeConfig(ConfigurationRoot())
        }
    }

    private fun readConfig() = File(filenameJson).readText(StandardCharsets.UTF_8).let { s: String ->
        objectMapper.readValue<ConfigurationRoot>(s)
    }

    private fun writeConfig(configurationRoot: ConfigurationRoot) {
        File(filenameJson).writeText(
            objectMapper
                .writer(DefaultPrettyPrinter().withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE))
                .writeValueAsString(configurationRoot)
        )
    }

    private fun update(fn: (ConfigurationRoot) -> ConfigurationRoot) {
        synchronized(this) {
            writeConfig(fn(readConfig()))
        }
    }

    fun isDevMode() = readConfig().devMode

    fun getHanDebugFile() = readConfig().hanDebugFile

    fun getLoraSerialPort() = readConfig().loraSerialPort
    fun getEnvironmentSensors() = readConfig().environmentSensors
    fun getUserSettings(userId: String) = readConfig().userSettings[userId]
    fun updateUserAuthorization(user: String, a: UserSettings) {
        update { c ->
            c.copy(userSettings = c.userSettings + (user to a))
        }
    }

    fun addUser(user: String) {
        update { c ->
            c.copy(userSettings = c.userSettings + (user to UserSettings(user, emptyMap())))
        }
    }

    fun removeUser(user: String) {
        update { c ->
            c.copy(userSettings = c.userSettings.filterNot { it.key == user })
        }
    }


    fun getAuthorization() = readConfig().userSettings
    fun getEnergyPriceServiceSettings() = readConfig().energyPriceServiceSettings
    fun getHeaterSettings() = readConfig().heatingControllerSettings
    fun setHeaterSettings(settings: HeatingControllerSettings) {
        update { c ->
            c.copy(
                heatingControllerSettings = settings
            )
        }
    }

    fun setEnvironmentSensor(s: EnvironmentSensor) {
        update { c ->
            c.copy(
                environmentSensors = c.environmentSensors.copy(
                    sensors = c.environmentSensors.sensors.filterNot { it.key == s.loraAddr } + (s.loraAddr to s)
                )
            )
        }
    }

    fun setEnergiPriceServiceSetting(serviceType: String, default: EnergyPriceServiceSettings) {
        update { c ->
            c.copy(
                energyPriceServiceSettings = c.energyPriceServiceSettings + (serviceType to default)
            )
        }
    }

    fun getEvSettings() = readConfig().evChargerSettings
    fun setEvSettings(evChargerSettings: EvChargerSettings) {
        update { c ->
            c.copy(
                evChargerSettings = evChargerSettings
            )
        }
    }

    fun getInfluxDbAuthToken() = readConfig().influxDbAuthToken
    fun getAesKeys() = readConfig().aes265Keys
    fun getVictronServiceSettings() = readConfig().victronService
    fun getVictronEssProcessSettings() = readConfig().victronEssProcess
    fun setVictronEssProcessSettings(s: VictronEssProcessSettings) {
        update { c ->
            c.copy(victronEssProcess = s)
        }
    }
}