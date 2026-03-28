package com.dehnes.smarthome.firewall_router

import com.dehnes.smarthome.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService


class FirewallServiceMock(
    private val executorService: ExecutorService,
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

    override fun refreshCachedState(user: String?) {
        val resp = mockedResponse

        updateState {
            var updated = it

            if (resp.serviceStates != null) {
                updated = updated.copy(serviceStates = resp.serviceStates)
            }
            if (resp.dnsBlockLists != null) {
                updated = updated.copy(dnsBlockLists = resp.dnsBlockLists)
            }

            updated
        }
    }

    override fun firewallWrite(user: String?, service: String, state: Map<String, Any>) {
        mockedResponse = mockedResponse.copy(
            serviceStates = mockedResponse.serviceStates!!.toMutableMap().mapValues {
                if (it.key == service) {
                    state
                } else {
                    it.value
                }
            }
        )
        refreshCachedState(user)
    }

    override fun dnsListSet(user: String?, enabledDnsBlockLists: List<String>) {
    }

    override fun dnsRefetchLists(user: String?) {
    }

}


var mockedResponse = """
{
  "serviceStates": {
  },
  "dnsBlockLists": [
    {
      "name": "youtube",
      "enabled": false,
      "changedAt": "2026-03-11T09:44:16.677Z"
    },
    {
      "name": "adware_malware",
      "enabled": true,
      "changedAt": "2026-03-11T09:44:16.679Z"
    },
    {
      "name": "social",
      "enabled": false,
      "changedAt": "2026-03-11T09:44:16.679Z"
    },
    {
      "name": "fakenews",
      "enabled": true,
      "changedAt": "2026-03-11T09:44:16.677Z"
    },
    {
      "name": "netflix",
      "enabled": false,
      "changedAt": "2026-03-11T09:44:16.677Z"
    },
    {
      "name": "gambling",
      "enabled": true,
      "changedAt": "2026-03-11T09:44:16.677Z"
    },
    {
      "name": "porn",
      "enabled": true,
      "changedAt": "2026-03-11T09:44:16.678Z"
    }
  ]
}
""".trimIndent().let {
    objectMapper.readValue<Response>(it)
}

