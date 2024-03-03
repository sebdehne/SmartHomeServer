package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.api.dtos.DnsBlockingState
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class FirewallService {

    private val logger = KotlinLogging.logger { }


    var currentState: FirewallState = FirewallState()
    val listeners: MutableMap<String, (FirewallState) -> Unit> =
        ConcurrentHashMap<String, (FirewallState) -> Unit>()

    fun updateState(fn: (currentState: FirewallState) -> FirewallState) {
        synchronized(this) {
            currentState = fn(currentState)
            listeners.forEach { l ->
                try {
                    l.value(currentState)
                } catch (e: Exception) {
                    logger.error(e) { "" }
                }
            }
        }
    }

    companion object {
        fun cmd(request: String): String {
            return synchronized(this) {
                val socket = Socket()
                socket.soTimeout = 30000
                socket.use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", 1000))
                    s.getOutputStream().write((request + "\r\n").toByteArray(StandardCharsets.UTF_8))
                    s.getInputStream().use {
                        it.readAllBytes().toString(StandardCharsets.UTF_8)
                    }.trim()
                }
            }
        }
    }
}

data class FirewallState(
    val dnsBlockingState: DnsBlockingState = DnsBlockingState(emptyMap()),
    val blockedMacState: BlockedMacState = BlockedMacState(emptyList()),
)

data class BlockedMacState(
    val blockedMacs: List<BlockedMac>,
)

data class BlockedMac(
    val name: String,
    val blocked: Boolean,
)
