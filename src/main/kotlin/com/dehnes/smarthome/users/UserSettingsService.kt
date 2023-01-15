package com.dehnes.smarthome.users

import com.dehnes.smarthome.api.dtos.WriteCommand
import com.dehnes.smarthome.api.dtos.WriteUserSettings
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.UserSettings
import mu.KotlinLogging

class UserSettingsService(private val configService: ConfigService) {

    private val logger = KotlinLogging.logger { }

    fun canUserRead(user: String?, userRole: UserRole): Boolean {
        val result = getUserAccessLevel(user, userRole) in listOf(Level.read, Level.readWrite, Level.readWriteAdmin)
        logger.info { "canUserRead: user=$user userRole=$userRole result=$result" }
        return result
    }

    fun canUserWrite(user: String?, userRole: UserRole): Boolean {
        val result = getUserAccessLevel(user, userRole) in listOf(Level.readWrite, Level.readWriteAdmin)
        logger.info { "canUserWrite: user=$user userRole=$userRole result=$result" }
        return result
    }

    fun canUserAdmin(user: String?, userRole: UserRole): Boolean {
        val result = getUserAccessLevel(user, userRole) == Level.readWriteAdmin
        logger.info { "canUserWrite: user=$user userRole=$userRole result=$result" }
        return result
    }

    fun getAllUserSettings(user: String?): Map<String, UserSettings> {
        check(canUserRead(user, UserRole.userSettings)) { "User $user cannot read userSettings" }
        return configService.getAuthorization().mapValues {
            getUserSettings(it.key)
        }
    }

    fun handleWrite(user: String?, writeUserSettings: WriteUserSettings) {
        check(canUserWrite(user, UserRole.userSettings)) { "User $user cannot write userSettings" }

        when (writeUserSettings.command) {
            WriteCommand.addUser -> configService.addUser(writeUserSettings.user)

            WriteCommand.removeUser -> configService.removeUser(writeUserSettings.user)

            WriteCommand.updateAuthorization -> {
                val existing = configService.getUserSettings(writeUserSettings.user)?.authorization ?: emptyMap()
                configService.updateUserAuthorization(
                    writeUserSettings.user,
                    UserSettings(
                        writeUserSettings.user,
                        existing + (writeUserSettings.writeAuthorization!!.userRole to writeUserSettings.writeAuthorization.level)
                    )
                )
            }
        }
    }

    private fun getUserAccessLevel(user: String?, userRole: UserRole) = if (user == SystemUser) {
        Level.read
    } else {
        getUserSettings(user).authorization[userRole] ?: userRole.defaultLevel
    }

    fun getUserSettings(user: String?) = (user ?: "unknown").let { u ->
        configService.getUserSettings(u) ?: UserSettings(u)
    }.let {
        it.copy(
            authorization = UserRole.values().associateWith {
                it.defaultLevel
            } + it.authorization
        )
    }

}

const val SystemUser = "SystemUser"

enum class UserRole(
    val defaultLevel: Level
) {
    garageDoor(Level.readWrite),
    evCharging(Level.read),
    energyStorageSystem(Level.read),
    energyPricing(Level.read),
    heaterUnderFloor(Level.read),
    environmentSensors(Level.read),
    cameras(Level.read),
    recordings(Level.none),
    userSettings(Level.none),
}

enum class Level {
    none,
    read,
    readWrite,
    readWriteAdmin
}
