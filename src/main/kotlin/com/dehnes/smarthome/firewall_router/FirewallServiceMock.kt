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
  "serviceStates" : {
    "PortForwarding" : {
      "elements" : [ {
        "name" : "rules",
        "description" : "port forward rules",
        "value" : [ ],
        "type" : "elementList",
        "writeable" : true,
        "templateElement" : {
          "value" : [ {
            "name" : "fromIp",
            "value" : "1.2.3.4/32",
            "type" : "stringNullable",
            "writeable" : true
          }, {
            "name" : "publicPort",
            "value" : 1234,
            "type" : "number",
            "writeable" : true
          }, {
            "name" : "dstIp",
            "value" : "192.168.1.2",
            "type" : "string",
            "writeable" : true
          }, {
            "name" : "dstPort",
            "value" : 22,
            "type" : "number",
            "writeable" : true
          } ],
          "type" : "elementList",
          "writeable" : true
        }
      } ]
    },
    "Unify" : {
      "elements" : [ ]
    },
    "Wireguard" : {
      "elements" : [ ]
    },
    "WireguardVpn" : {
      "elements" : [ ]
    },
    "RouterInputV4" : {
      "elements" : [ {
        "name" : "services",
        "description" : "Services",
        "value" : [ {
          "value" : [ {
            "name" : "name",
            "value" : "ssh",
            "type" : "string",
            "writeable" : false
          }, {
            "name" : "sources",
            "value" : [ {
              "value" : [ {
                "name" : "source",
                "value" : "52.29.236.132/32",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            }, {
              "value" : [ {
                "name" : "source",
                "value" : "192.168.1.0/24",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            } ],
            "type" : "elementList",
            "writeable" : true,
            "templateElement" : {
              "value" : [ {
                "name" : "source",
                "value" : "1.2.3.4/32",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            }
          } ],
          "type" : "elementList",
          "writeable" : false
        }, {
          "value" : [ {
            "name" : "name",
            "value" : "evCharging",
            "type" : "string",
            "writeable" : false
          }, {
            "name" : "sources",
            "value" : [ {
              "value" : [ {
                "name" : "source",
                "value" : "192.168.1.0/24",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            } ],
            "type" : "elementList",
            "writeable" : true,
            "templateElement" : {
              "value" : [ {
                "name" : "source",
                "value" : "1.2.3.4/32",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            }
          } ],
          "type" : "elementList",
          "writeable" : false
        }, {
          "value" : [ {
            "name" : "name",
            "value" : "firewall",
            "type" : "string",
            "writeable" : false
          }, {
            "name" : "sources",
            "value" : [ {
              "value" : [ {
                "name" : "source",
                "value" : "127.0.0.1/32",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            } ],
            "type" : "elementList",
            "writeable" : true,
            "templateElement" : {
              "value" : [ {
                "name" : "source",
                "value" : "1.2.3.4/32",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            }
          } ],
          "type" : "elementList",
          "writeable" : false
        }, {
          "value" : [ {
            "name" : "name",
            "value" : "zwave",
            "type" : "string",
            "writeable" : false
          }, {
            "name" : "sources",
            "value" : [ {
              "value" : [ {
                "name" : "source",
                "value" : "192.168.1.0/24",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            } ],
            "type" : "elementList",
            "writeable" : true,
            "templateElement" : {
              "value" : [ {
                "name" : "source",
                "value" : "1.2.3.4/32",
                "type" : "string",
                "writeable" : true
              }, {
                "name" : "isAccept",
                "value" : true,
                "type" : "boolean",
                "writeable" : true
              } ],
              "type" : "elementList",
              "writeable" : false
            }
          } ],
          "type" : "elementList",
          "writeable" : false
        } ],
        "type" : "elementList",
        "writeable" : false
      } ]
    },
    "WebRTC" : {
      "elements" : [ ]
    },
    "WebServer" : {
      "elements" : [ ]
    },
    "NoExternalDns" : {
      "elements" : [ {
        "name" : "macWhitelist",
        "description" : "MAC whitelist",
        "value" : [ {
          "value" : [ {
            "name" : "mac",
            "description" : "MAC address",
            "value" : "90:0c:c8:ee:9c:2d",
            "type" : "string",
            "writeable" : true
          }, {
            "name" : "isAccept",
            "description" : "is allow?",
            "value" : true,
            "type" : "boolean",
            "writeable" : true
          } ],
          "type" : "elementList",
          "writeable" : true
        }, {
          "value" : [ {
            "name" : "mac",
            "description" : "MAC address",
            "value" : "58:f9:87:fa:b2:42",
            "type" : "string",
            "writeable" : true
          }, {
            "name" : "isAccept",
            "description" : "is allow?",
            "value" : true,
            "type" : "boolean",
            "writeable" : true
          } ],
          "type" : "elementList",
          "writeable" : true
        } ],
        "type" : "elementList",
        "writeable" : true,
        "templateElement" : {
          "value" : [ {
            "name" : "mac",
            "description" : "MAC address",
            "value" : "01:a2:b3:c4:d5:e6",
            "type" : "string",
            "writeable" : true
          }, {
            "name" : "isAccept",
            "description" : "is allow?",
            "value" : true,
            "type" : "boolean",
            "writeable" : true
          } ],
          "type" : "elementList",
          "writeable" : true
        }
      } ]
    },
    "BlockMac" : {
      "elements" : [ {
        "name" : "blockedMacs",
        "description" : "MAC addresses that are blocked",
        "value" : [ ],
        "type" : "stringList",
        "writeable" : true
      } ]
    },
    "AppInfluxDb" : {
      "elements" : [ {
        "name" : "internetAccess",
        "description" : "Does influxDb VM have internet access",
        "value" : false,
        "type" : "boolean",
        "writeable" : true
      }, {
        "name" : "subnet",
        "description" : "Subnet",
        "value" : "10.1.0.0/24",
        "type" : "string",
        "writeable" : false
      }, {
        "name" : "int",
        "description" : "Router interface",
        "value" : "app_influxdb",
        "type" : "string",
        "writeable" : false
      }, {
        "name" : "gatewayIp",
        "description" : "Gateway IP",
        "value" : "10.1.0.1",
        "type" : "string",
        "writeable" : false
      }, {
        "name" : "influxDbIp",
        "description" : "influxDb Ip",
        "value" : "10.1.0.3",
        "type" : "string",
        "writeable" : false
      } ]
    },
    "Dns" : {
      "elements" : [ ]
    }
  },
  "dnsBlockLists" : [ {
    "name" : "youtube",
    "enabled" : false,
    "changedAt" : "2026-03-11T09:44:16.677Z"
  }, {
    "name" : "adware_malware",
    "enabled" : true,
    "changedAt" : "2026-03-11T09:44:16.679Z"
  }, {
    "name" : "social",
    "enabled" : false,
    "changedAt" : "2026-03-11T09:44:16.679Z"
  }, {
    "name" : "fakenews",
    "enabled" : true,
    "changedAt" : "2026-03-11T09:44:16.677Z"
  }, {
    "name" : "netflix",
    "enabled" : false,
    "changedAt" : "2026-03-11T09:44:16.677Z"
  }, {
    "name" : "gambling",
    "enabled" : true,
    "changedAt" : "2026-03-11T09:44:16.677Z"
  }, {
    "name" : "porn",
    "enabled" : true,
    "changedAt" : "2026-03-11T09:44:16.678Z"
  } ]
}
""".trimIndent().let {
    objectMapper.readValue<Response>(it)
}

