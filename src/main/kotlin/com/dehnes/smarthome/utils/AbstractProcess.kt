package com.dehnes.smarthome.utils

import io.github.oshai.kotlinlogging.KLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractProcess(
    val executorService: ExecutorService,
    private val intervalInSeconds: Long
) {

    protected val timer = Executors.newSingleThreadScheduledExecutor()
    private val runLock = ReentrantLock()

    open fun start() {
        timer.scheduleWithFixedDelay({
            executorService.submit(withLogging {
                tick()
            })
        }, intervalInSeconds, intervalInSeconds, TimeUnit.SECONDS)
    }

    fun tick(): Boolean = if (runLock.tryLock()) {
        try {
            tickLocked()
        } catch (t: Throwable) {
            errorLogger.error(t) { "" }
            false
        } finally {
            runLock.unlock()
        }
    } else {
        false
    }

    protected fun <T> asLocked(fn: () -> T): T? {
        return if (runLock.tryLock(10, TimeUnit.SECONDS)) {
            try {
                fn()
            } finally {
                runLock.unlock()
            }
        } else {
            null
        }
    }

    abstract fun tickLocked(): Boolean
    abstract fun logger(): KLogger
}