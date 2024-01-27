package com.dehnes.smarthome.utils

import io.github.oshai.kotlinlogging.KotlinLogging

val errorLogger = KotlinLogging.logger("com.dehnes.smarthome.utils.ErrorLogger")

fun withLogging(fn: () -> Unit) = Runnable {
    try {
        fn()
    } catch (t: Throwable) {
        errorLogger.error(t) { "" }
    }
}