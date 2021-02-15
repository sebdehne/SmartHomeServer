package com.dehnes.smarthome.service

import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractProcess(
    private val executorService: ExecutorService,
    private val intervalInSeconds: Long
) {

    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val runLock = ReentrantLock()

    fun start() {
        timer.scheduleAtFixedRate({
            executorService.submit {
                try {
                    tick()
                } catch (e: Exception) {
                    logger().error("", e)
                }
            }
        }, intervalInSeconds, intervalInSeconds, TimeUnit.SECONDS)
    }

    fun tick(): Boolean = if (runLock.tryLock()) {
        try {
            tickLocked()
        } finally {
            runLock.unlock()
        }
    } else {
        false
    }

    abstract fun tickLocked(): Boolean
    abstract fun logger(): Logger
}