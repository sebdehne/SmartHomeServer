package com.dehnes.smarthome.firewall_router

interface FirewallService {
    fun start()
    fun refreshCachedState(user: String?)
    fun firewallWrite(user: String?, service: String, state: Map<String, Any>)
    fun dnsListSet(user: String?, enabledDnsBlockLists: List<String>)
    fun dnsRefetchLists(user: String?)

    val currentState: FirewallState
    val listeners: MutableMap<String, (FirewallState) -> Unit>
}

data class FirewallState(
    val serviceStates: Map<String, Map<String, Any>>? = null,
    val dnsBlockLists: List<Map<String, Any>>? = null,
)