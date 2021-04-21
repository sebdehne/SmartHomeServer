package com.dehnes.smarthome.utils

import java.time.Clock

fun Clock.timestampSecondsSince2000(): Long {
    return (this.millis() / 1000) - 946_684_800L
}

