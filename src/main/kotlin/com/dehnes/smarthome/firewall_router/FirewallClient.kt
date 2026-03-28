package com.dehnes.smarthome.firewall_router


data class Response(
    val error: String? = null,
    val serviceStates: Map<String, Map<String, Any>>? = null,
    val dnsBlockLists: List<DnsBlockList>? = null,
)

