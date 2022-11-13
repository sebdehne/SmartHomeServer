package com.dehnes.smarthome.users

import com.dehnes.smarthome.utils.PersistenceService
import mu.KotlinLogging

class UserSettingsService(private val persistenceService: PersistenceService) {

    private val logger = KotlinLogging.logger { }

    fun canUserRead(user: String?, userRole: UserRole): Boolean {
        val result = getUserAccessLevel(user, userRole) == Level.read
        logger.info { "canUserRead: user=$user userRole=$userRole result=$result" }
        return result
    }

    fun canUserWrite(user: String?, userRole: UserRole): Boolean {
        val result = getUserAccessLevel(user, userRole) == Level.readWrite
        logger.info { "canUserWrite: user=$user userRole=$userRole result=$result" }
        return result
    }

    fun getUserAccessLevel(user: String?, userRole: UserRole): Level {
        return if (user == SystemUser) {
            Level.readWrite
        } else {
            Level.valueOf(persistenceService["UserSettingsService.auth.${userKey(user)}.$userRole", Level.none.name]!!)
        }
    }

    fun getUserSettings(user: String?) = UserSettings(
        authorization = UserRole.values().associateWith { getUserAccessLevel(user, it) }
    )

    private fun userKey(user: String?) = user?.replace(".", "_") ?: "unknown"

}

const val SystemUser = "SystemUser"

data class UserSettings(
    val authorization: Map<UserRole, Level>,
)

enum class UserRole {
    garageDoor,
    evCharging,
    energyStorageSystem,
    energyPricing,
    heaterUnderFloor,
    environmentSensors,
    cameras,
    recordings,
    firmwareUpgrades,
}

enum class Level {
    none,
    read,
    readWrite,
}
