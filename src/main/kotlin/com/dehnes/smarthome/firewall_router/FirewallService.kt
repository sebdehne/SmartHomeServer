package com.dehnes.smarthome.firewall_router

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService


class FirewallService(
    private val firewallClient: FirewallClient,
    private val executorService: ExecutorService,
) {

    private val logger = KotlinLogging.logger { }

    var currentState: FirewallState = FirewallState()
    val listeners: MutableMap<String, (FirewallState) -> Unit> =
        ConcurrentHashMap<String, (FirewallState) -> Unit>()

    fun start() {
        executorService.submit {
            try {
                refreshCachedState()
            } catch (e: Exception) {
                logger.error(e) { "" }
            }
        }
    }

    fun updateState(fn: (currentState: FirewallState) -> FirewallState) {
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

    fun refreshCachedState() {
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

    fun firewallWrite(service: String, state: Map<String, Any>) {
        val resp = firewallClient.rpc(Request(type = "updateService", service = service, serviceInput = state))
        check(resp.error == null) { "Error updating firewall service state: ${resp.error}" }

        refreshCachedState()
    }

    fun dnsListSet(enabledDnsBlockLists: List<String>) {
        val resp = firewallClient.rpc(Request(type = "dns_lists_set_and_reload", enabledDnsBlockLists = enabledDnsBlockLists))
        check(resp.error == null) { "Error updating firewall dns list state: ${resp.error}" }

        refreshCachedState()
    }

    fun dnsRefetchLists() {
        val resp = firewallClient.rpc(Request(type = "dns_lists_update"))
        check(resp.error == null) { "Error updating dns lists: ${resp.error}" }

        refreshCachedState()
    }

}

data class FirewallState(
    val serviceStates: Map<String, Map<String, Any>>? = null,
    val dnsBlockLists: List<Map<String, Any>>? = null,
)
