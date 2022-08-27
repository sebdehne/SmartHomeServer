package com.dehnes.smarthome.han

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord

class HanDataService(
    private val influxDBClient: InfluxDBClient
) {

    fun onNewData(hanData: HanData) {
        influxDBClient.recordSensorData(
            InfluxDBRecord(
                hanData.createdAt,
                "electricityData",
                listOfNotNull(
                    "totalPowerImport" to hanData.totalPowerImport, // in Watt
                    "totalPowerExport" to hanData.totalPowerExport, // in Watt
                    "totalReactivePowerImport" to hanData.totalReactivePowerImport,
                    "totalReactivePowerExport" to hanData.totalReactivePowerExport,
                    "currentL1" to hanData.currentL1 * 10, // in milliAmpere
                    "currentL2" to hanData.currentL2 * 10, // in milliAmpere
                    "currentL3" to hanData.currentL3 * 10, // in milliAmpere
                    "voltageL1" to hanData.voltageL1, // in Volt
                    "voltageL2" to hanData.voltageL2, // in Volt
                    "voltageL3" to hanData.voltageL3, // in Volt
                    hanData.totalEnergyImport?.let { "totalEnergyImport" to it },
                    hanData.totalEnergyExport?.let { "totalEnergyExport" to it },
                    hanData.totalReactiveEnergyImport?.let { "totalReactiveEnergyImport" to it },
                    hanData.totalReactiveEnergyExport?.let { "totalReactiveEnergyExport" to it },
                ).toMap(),
                mapOf(
                    "sensor" to "MainMeter"
                )
            )
        )
    }

}