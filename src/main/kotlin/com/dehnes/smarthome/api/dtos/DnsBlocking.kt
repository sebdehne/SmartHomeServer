package com.dehnes.smarthome.api.dtos

import java.time.Instant

data class DnsBlockingState(
    val listsToEnabled: Map<String, DnsBlockingListState>,
)

data class DnsBlockingListState(
    val enabled: Boolean,
    val lastUpdated: Instant
)

