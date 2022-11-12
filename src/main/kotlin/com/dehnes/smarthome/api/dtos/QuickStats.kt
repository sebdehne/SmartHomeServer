package com.dehnes.smarthome.api.dtos

import com.dehnes.smarthome.victron.SystemState

data class QuickStatsResponse(
    val powerImportInWatts: Long,
    val powerExportInWatts: Long,
    val costEnergyImportedToday: Double,
    val costEnergyImportedCurrentMonth: Double,
    val energyImportedTodayWattHours: Long,
    val outsideTemperature: Double,
    val currentEnergyPrice: Double,
    val essSystemStatus: SystemState,
    val essBatteryPower: Long,
    val essBatterySoC: Int,
)
