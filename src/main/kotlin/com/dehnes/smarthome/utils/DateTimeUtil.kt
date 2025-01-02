package com.dehnes.smarthome.utils

import java.time.Clock
import java.time.Instant

fun Clock.timestampSecondsSince2000(): Long {
    return (this.millis() / 1000) - 946_684_800L
}

fun Instant.timestampSecondsSince2000(): Long {
    return (this.toEpochMilli() / 1000) - 946_684_800L // 946684800
}