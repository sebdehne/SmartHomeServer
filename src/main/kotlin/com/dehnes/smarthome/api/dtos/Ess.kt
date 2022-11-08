package com.dehnes.smarthome.api.dtos

data class EssRequest(
    val acPowerSetPoint: Long? = null,
    val maxChargePower: Long? = null,
    val maxDischargePower: Long? = null,
)
