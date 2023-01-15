package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.users.Level
import com.dehnes.smarthome.users.UserRole


data class WriteUserSettings(
    val user: String,
    val command: WriteCommand,
    val writeAuthorization: WriteAuthorization?
)

enum class WriteCommand {
    addUser,
    removeUser,
    updateAuthorization
}

data class WriteAuthorization(
    val userRole: UserRole,
    val level: Level,
)
