package com.dehnes.smarthome.external

import com.dehnes.smarthome.rf433.Rf433Client
import com.dehnes.smarthome.rf433.RfPacket
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

@Disabled("connects to external service")
internal class Rf433ClientIT {

    @Test
    fun test() {
        val rf433Client = Rf433Client(
            Executors.newCachedThreadPool(),
            "192.168.1.1"
        )
        rf433Client.listeners.add { rfPacket ->
            println(rfPacket)
        }

        rf433Client.start()

        // communicate with heater
        Thread.sleep(1000)
        rf433Client.send(RfPacket(27, intArrayOf(1)))
        Thread.sleep(1000)
        rf433Client.send(RfPacket(27, intArrayOf(1)))
        Thread.sleep(1000)
        rf433Client.send(RfPacket(27, intArrayOf(1)))

        Thread.sleep(10000)

    }
}