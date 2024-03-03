package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.firewall_router.FirewallService.Companion.cmd
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService

class BlockedMacs(
    private val userSettingsService: UserSettingsService,
    private val firewallService: FirewallService,
    private val configService: ConfigService,
) {

    fun start() {
        val blockedMacState = get(null, bypassAuth = true)
        firewallService.updateState {
            it.copy(blockedMacState = blockedMacState)
        }
    }

    fun get(user: String?, bypassAuth: Boolean = false): BlockedMacState {
        check(
            bypassAuth || userSettingsService.canUserRead(
                user,
                UserRole.firewall
            )
        ) { "User $user cannot read blockedMacs state" }

        val blockedMacs = cmd("getServiceStatus BlockMac")
            .split(",")
            .map { it.lowercase() }

        val knownMacs = configService.getKnownNetworkDevices()

        val list = knownMacs.entries.map {
            BlockedMac(
                name = it.key,
                blocked = it.value.lowercase() in blockedMacs
            )
        }

        return BlockedMacState(list)
    }

    fun set(user: String?, names: List<String>) {
        check(
            userSettingsService.canUserWrite(
                user,
                UserRole.firewall
            )
        ) { "User $user cannot write blockedMacs state" }

        val knownMacs = configService.getKnownNetworkDevices()

        val macs = names.mapNotNull {
            val mac = knownMacs[it] ?: return@mapNotNull null
            mac
        }.joinToString(",")

        cmd("updateServices BlockMac $macs")

        val status = get(user)
        firewallService.updateState {
            it.copy(blockedMacState = status)
        }
    }

}