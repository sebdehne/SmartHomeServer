package com.dehnes.smarthome.api.dtos

data class FirewallRequestData(
    val serviceState: ServiceState?,
    val enabledDnsBlockLists: List<String>? = null,
)

data class ServiceState(
    val service: String,
    val state: Map<String, Any>,
)