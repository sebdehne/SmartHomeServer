package com.dehnes.smarthome.api.dtos

data class QuickStatsResponse(
    val powerImportInWatts: Long,
    val powerExportInWatts: Long,
    val costEnergyImportedToday: Double,
    val costEnergyImportedCurrentMonth: Double,
    val energyImportedTodayWattHours: Long,
    val outsideTemperature: Double,
    val currentEnergyPrice: Double,
)
