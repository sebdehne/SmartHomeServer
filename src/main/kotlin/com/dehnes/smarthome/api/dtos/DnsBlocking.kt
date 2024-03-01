package com.dehnes.smarthome.api.dtos

data class DnsBlockingState(
    val listsToEnabled: Map<String, Boolean>,
)

