package com.dehnes.smarthome.config

import com.dehnes.smarthome.api.dtos.EvChargingMode
import com.dehnes.smarthome.api.dtos.Mode
import com.dehnes.smarthome.api.dtos.OnOff
import com.dehnes.smarthome.ev_charging.CalibrationData
import com.dehnes.smarthome.ev_charging.LoadSharingPriority
import com.dehnes.smarthome.users.Level
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.victron.VictronEssProcess
import com.dehnes.smarthome.zwave.StairsHeatingSettings

data class ConfigurationRoot(
    val devMode: Boolean = false,
    val hanDebugFile: String? = null,
    val loraSerialPort: String = "/dev/ttyACM0",
    val userSettings: Map<String, UserSettings> = emptyMap(),
    val environmentSensors: EnvironmentSensors = EnvironmentSensors(),
    val stairsHeatingSettings: StairsHeatingSettings = StairsHeatingSettings(),
    val energyPriceServiceSettings: Map<String, EnergyPriceServiceSettings> = emptyMap(),
    val heatingControllerSettings: HeatingControllerSettings = HeatingControllerSettings(),
    val evChargerSettings: EvChargerSettings = EvChargerSettings(),
    val influxDbAuthToken: String = "AuthToken",
    val aes265Keys: Map<String, String> = emptyMap(),
    val victronService: VictronService = VictronService(),
    val victronEssProcess: VictronEssProcessSettings = VictronEssProcessSettings(),
    val knownNetworkDevices: Map<String, String> = emptyMap(),
)

data class VictronEssProcessSettings(
    val currentOperationMode: VictronEssProcess.OperationMode = VictronEssProcess.OperationMode.passthrough,
    val minNumberOfOnlineBmses: Int = 3,
    val bmsAssumeDeadAfterSeconds: Int = 30,
    val socLimitTo: Int = 90,
    val socLimitFrom: Int = 20,
    val profiles: Map<String, VictronEssProcessProfile> = emptyMap()
)

data class VictronEssProcessProfile(
    val acPowerSetPoint: Long = 10000,
    val maxChargePower: Long = 10000,
    val maxDischargePower: Long = -1
)

data class VictronService(
    val portalId: String = "",
    val writeEnabled: Boolean = false,
)

data class EvChargerSettings(
    val stayInChargingForMS: Int = 30000,
    val powerConnections: Map<String, PowerConnectionSettings> = emptyMap(),
    val chargers: Map<String, EvCharger> = emptyMap(),
    val chargingEndingAmpDelta: Int = 2,
    val stayInStoppingChargingForMS: Int = 15000,
    val assumeStationLostAfterMs: Int = 300000,
)

data class PowerConnectionSettings(
    val name: String = "unknown",
    val availableCapacity: Int = 32,
    val loadSharingAlgorithm: String = "PriorityLoadSharing",
)

data class EvCharger(
    val serialNumber: String,
    val name: String,
    val displayName: String,
    val powerConnection: String,
    val calibrationData: CalibrationData,
    val evChargingMode: EvChargingMode,
    val priority: LoadSharingPriority,
    val chargeRateLimit: Int,
)

data class EnergyPriceServiceSettings(
    val avgMultiplier: Double,
    val neutralSpan: Double,
)

data class EnvironmentSensors(
    val sensors: Map<String, EnvironmentSensor> = emptyMap(),
    val validateTimestamp: Boolean = true,
)

data class EnvironmentSensor(
    val id: Int,
    val loraAddr: String,
    val name: String = "unknown",
    val displayName: String = "Unknown name",
    val ignore: Boolean = false,
    val sleepTimeInSeconds: Int = 300,
    val fiveVoltADC: Int = 3109,
)


data class UserSettings(
    val user: String,
    val authorization: Map<UserRole, Level> = emptyMap()
)


data class HeatingControllerSettings(
    val heaterTarget: OnOff = OnOff.off,
    val operatingMode: Mode = Mode.MANUAL,
    val automaticMode: Boolean = false,
    val targetTemp: Int = 24,
)