package com.dehnes.smarthome.dns_blocking

import com.dehnes.smarthome.api.dtos.DnsBlockingState
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class DnsBlockingService(
    private val userSettingsService: UserSettingsService,
    private val configService: ConfigService,
) {

    private val logger = KotlinLogging.logger {}

    var dnsBlockingState: DnsBlockingState? = null
    val listeners: MutableMap<String, (DnsBlockingState) -> Unit> =
        ConcurrentHashMap<String, (DnsBlockingState) -> Unit>()

    private fun setNewState(s: DnsBlockingState) {
        dnsBlockingState = s
        listeners.forEach { l ->
            try {
                l.value(s)
            } catch (e: Exception) {
                logger.error(e) { "" }
            }
        }
    }

    fun set(user: String?, lists: List<String>) {
        check(
            userSettingsService.canUserWrite(
                user,
                UserRole.dnsBlocking
            )
        ) { "User $user cannot update dns blocking settings" }

        if (configService.isDevMode()) {
            return
        }

        val response = cmd("dns_lists_set_and_reload " + lists.joinToString(",")).trim()
        check(!response.contains("ERROR"))

        setNewState(get(user))
    }

    fun get(user: String?): DnsBlockingState {
        check(
            userSettingsService.canUserRead(
                user,
                UserRole.dnsBlocking
            )
        ) { "User $user cannot read dns blocking state" }

        if (configService.isDevMode()) {
            return DnsBlockingState(mapOf("testlist1" to false, "testlist2" to true))
        }

        val respons = cmd("dns_lists_get").trim()

        return DnsBlockingState(
            listsToEnabled = respons.split(",")
                .map { it.trim() }
                .associate {
                    val (l, r) = it.split("=")
                    l to r.toBoolean()
                }
        )
    }

    fun updateStandardLists() {
        val response = cmd("dns_lists_update")
        check(!response.contains("ERROR"))
    }

    companion object {
        fun cmd(request: String): String {
            val socket = Socket()
            socket.soTimeout = 30000
            return try {
                socket.connect(InetSocketAddress("127.0.0.1", 1000))
                socket.getOutputStream().write((request + "\r\n").toByteArray(StandardCharsets.UTF_8))
                socket.getInputStream().use {
                    it.readAllBytes().toString(StandardCharsets.UTF_8)
                }
            } finally {
                socket.close()
            }
        }
    }
}
