package com.dehnes.smarthome.external

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

@Disabled("connects to external service")
internal class SerialConnectionIT {

    @Test
    fun test() {
        val serialConnection = SerialConnection(
            Executors.newCachedThreadPool(),
            "192.168.1.1"
        )
        serialConnection.listeners.add { rfPacket ->
            println(rfPacket)
        }

        serialConnection.start()

        // communicate with heater
        Thread.sleep(1000)
        serialConnection.send(RfPacket(27, intArrayOf(1)))
        Thread.sleep(1000)
        serialConnection.send(RfPacket(27, intArrayOf(1)))
        Thread.sleep(1000)
        serialConnection.send(RfPacket(27, intArrayOf(1)))

        Thread.sleep(10000)

    }
}