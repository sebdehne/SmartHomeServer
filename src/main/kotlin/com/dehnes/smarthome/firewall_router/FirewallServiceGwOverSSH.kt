package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.CmdExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class FirewallServiceGwOverSSH(
    private val executorService: ExecutorService,
    private val userSettingsService: UserSettingsService,
) : FirewallService {

    private val logger = KotlinLogging.logger { }

    override var currentState: FirewallState = FirewallState()
    override val listeners: MutableMap<String, (FirewallState) -> Unit> =
        ConcurrentHashMap<String, (FirewallState) -> Unit>()

    override fun start() {
        executorService.submit {
            try {
                refreshCachedState(null)
            } catch (e: Exception) {
                logger.error(e) { "" }
            }
        }
    }

    private fun runCmd(arg: String) = CmdExecutor.runToString(
        listOf(
            "/bin/sh",
            "-c",
            "ssh smarthome@10.4.0.1 \"cd blocklists && doas /home/smarthome/blocklists/blocklists $arg\""
        )
    )

    private fun unboundReload() = CmdExecutor.runToString(
        listOf(
            "/bin/sh",
            "-c",
            "ssh smarthome@10.4.0.1 \"doas /usr/sbin/unbound-control reload\""
        )
    )

    override fun refreshCachedState(user: String?) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return

        val unboundLists = runCmd("unbound-status")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .associate {
                val line = it.trim()
                val split = line.split(" ")
                val l = DnsBlockList(
                    split.first(),
                    split.last().toBoolean(),
                    Instant.now()
                )
                l.name to l
            }
            .toMutableMap()

        runCmd("lists-status")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val split = line.split(" ")
                val name = split.first()
                val timestamp = if (split.size < 3) {
                    Instant.ofEpochMilli(0).toString()
                } else {
                    split[2]
                }
                unboundLists.computeIfPresent(name) { _, v ->
                    v.copy(
                        changedAt = Instant.parse(timestamp),
                    )
                }
            }

        updateState {
            it.copy(dnsBlockLists = unboundLists.values.toList().sortedBy { it.name })
        }
    }

    private fun updateState(fn: (currentState: FirewallState) -> FirewallState) {
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

    override fun firewallWrite(
        user: String?,
        service: String,
        state: Map<String, Any>
    ) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return
    }

    override fun dnsListSet(user: String?, enabledDnsBlockLists: List<String>) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return

        runCmd("unbound-update ${enabledDnsBlockLists.joinToString(" ")}")

        if ("dns-over-https-domains" in enabledDnsBlockLists) {
            runCmd("pf-update dns-over-https dns-over-httpsV4 dns-over-httpsV6")
        } else {
            runCmd("pf-update dns-over-https")
        }
        refreshCachedState(user)
        unboundReload()
    }

    override fun dnsRefetchLists(user: String?) {
        if (!userSettingsService.canUserWrite(user, UserRole.firewall)) return
        runCmd("lists-refetch")
    }
}

