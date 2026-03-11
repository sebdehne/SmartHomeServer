package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.api.dtos.DnsBlockingState
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap


class FirewallService(private val socketFile: String) {

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

    fun cmd(request: String): String {
        return synchronized(this) {
            SocketChannel.open(UnixDomainSocketAddress.of(Path.of(socketFile))).use { client ->
                val reader = BufferedReader(InputStreamReader(Channels.newInputStream(client), "UTF-8"))
                client.write(ByteBuffer.wrap("$request\r\n".toByteArray(StandardCharsets.UTF_8)))
                val lines = mutableListOf<String>()
                while(true) {
                    val l = reader.readLine() ?: break
                    lines.add(l)
                }
                lines.joinToString("\r\n")
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
