package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService


class FirewallServiceImpl(
    private val firewallClient: FirewallClient,
    private val executorService: ExecutorService,
    private val userSettingsService: UserSettingsService,
): FirewallService {

    private val logger = KotlinLogging.logger { }

    override var currentState: FirewallState = FirewallState()
    override val listeners: MutableMap<String, (FirewallState) -> Unit> =
        ConcurrentHashMap<String, (FirewallState) -> Unit>()

    override fun start() {
        executorService.submit {
            try {
                refreshCachedState(null)
            } catch (e: Exception) {
                logger.error(e) { "" }
            }
        }
    }

    private fun updateState(fn: (currentState: FirewallState) -> FirewallState) {
        synchronized(this) {
            currentState = fn(currentState)
            listeners.forEach { l ->
                try {
                    l.value(currentState)
                } catch (e: Exception) {
                    logger.error(e) { "" }
                }
            }
        }
    }

    override fun refreshCachedState(user: String?) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return
        val resp = firewallClient.rpc(Request(type = "getServicesStatus"))

        updateState {
            var updated = it

            if (resp.serviceStates != null) {
                updated = updated.copy(serviceStates = resp.serviceStates)
            }
            if (resp.dnsBlockLists != null) {
                updated = updated.copy(dnsBlockLists = resp.dnsBlockLists)
            }

            updated
        }
    }

    override fun firewallWrite(user: String?, service: String, state: Map<String, Any>) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return
        val resp = firewallClient.rpc(Request(type = "updateService", service = service, serviceInput = state))
        check(resp.error == null) { "Error updating firewall service state: ${resp.error}" }

        refreshCachedState(user)
    }

    override fun dnsListSet(user: String?, enabledDnsBlockLists: List<String>) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return
        val resp = firewallClient.rpc(Request(type = "dns_lists_set_and_reload", enabledDnsBlockLists = enabledDnsBlockLists))
        check(resp.error == null) { "Error updating firewall dns list state: ${resp.error}" }

        refreshCachedState(user)
    }

    override fun dnsRefetchLists(user: String?) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return
        val resp = firewallClient.rpc(Request(type = "dns_lists_update"))
        check(resp.error == null) { "Error updating dns lists: ${resp.error}" }

        refreshCachedState(user)
    }

}


