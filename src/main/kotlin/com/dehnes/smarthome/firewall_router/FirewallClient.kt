package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class FirewallClient(
    private val firewallHost: String,
    private val firewallPort: Int,
) {

    fun rpc(req: Request): Response = Socket().apply {
        soTimeout = 30000
        connect(InetSocketAddress(firewallHost, firewallPort), 5000)
    }.use { socket ->
        socket.getOutputStream().write(
            "${objectMapper.writeValueAsString(req)}\n\n".toByteArray(StandardCharsets.UTF_8)
        )

        val resp = socket.getInputStream().readAllBytes().toString(StandardCharsets.UTF_8)
        if (resp.startsWith("ERROR: ")) error("For response: $resp")
        objectMapper.readValue(resp)
    }

}

data class Response(
    val error: String? = null,
    val serviceStates: Map<String, Map<String, Any>>? = null,
    val dnsBlockLists: List<Map<String, Any>>? = null,
)

data class Request(
    val type: String,
    val service: String? = null,
    val serviceInput: Map<String, Any>? = null,
    val enabledDnsBlockLists: List<String>? = null,
)
