package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.api.dtos.DnsBlockingListState
import com.dehnes.smarthome.api.dtos.DnsBlockingState
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.firewall_router.FirewallService.Companion.cmd
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import java.time.Instant

class DnsBlockingService(
    private val userSettingsService: UserSettingsService,
    private val configService: ConfigService,
    private val firewallService: FirewallService,
) {

    fun start() {
        val dnsBlockingState = get(null, bypassAuth = true)
        firewallService.updateState {
            it.copy(dnsBlockingState = dnsBlockingState)
        }
    }

    fun set(user: String?, lists: List<String>) {
        check(
            userSettingsService.canUserWrite(
                user,
                UserRole.firewall
            )
        ) { "User $user cannot update dns blocking settings" }

        if (configService.isDevMode()) {
            return
        }

        val response = cmd("dns_lists_set_and_reload " + lists.joinToString(",")).trim()
        check(!response.contains("ERROR"))

        val dnsBlockingState = get(user)
        firewallService.updateState {
            it.copy(dnsBlockingState = dnsBlockingState)
        }
    }

    fun get(user: String?, bypassAuth: Boolean = false): DnsBlockingState {
        check(
            bypassAuth || userSettingsService.canUserRead(
                user,
                UserRole.firewall
            )
        ) { "User $user cannot read dns blocking state" }

        if (configService.isDevMode()) {
            return DnsBlockingState(
                mapOf(
                    "testlist1" to DnsBlockingListState(true, Instant.now()),
                    "testlist2" to DnsBlockingListState(false, Instant.now())
                )
            )
        }

        val respons = cmd("dns_lists_get").lines()

        return DnsBlockingState(
            listsToEnabled = respons
                .mapNotNull {
                    val split = it.split(" ")
                    if (split.size != 3) return@mapNotNull null

                    split[0] to DnsBlockingListState(
                        split[1].toBoolean(),
                        Instant.parse(split[2]),
                    )
                }.toMap()
        )
    }

    fun updateStandardLists(user: String?) {
        check(
            userSettingsService.canUserWrite(
                user,
                UserRole.firewall
            )
        ) { "User $user cannot write dns blocking state" }

        val response = cmd("dns_lists_update")
        check(!response.contains("ERROR"))

        val dnsBlockingState = get(user)
        firewallService.updateState {
            it.copy(dnsBlockingState = dnsBlockingState)
        }
    }

}
