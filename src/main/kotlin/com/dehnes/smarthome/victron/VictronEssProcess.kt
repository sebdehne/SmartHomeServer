package com.dehnes.smarthome.victron

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.PriceCategory
import com.dehnes.smarthome.energy_pricing.priceDecision
import com.dehnes.smarthome.energy_pricing.serviceEnergyStorage
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.DateTimeUtils.zoneId
import com.dehnes.smarthome.utils.PersistenceService
import com.dehnes.smarthome.victron.VictronEssCalculation.VictronEssCalculationInput
import com.dehnes.smarthome.victron.VictronEssCalculation.calculateAcPowerSetPoints
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import mu.KotlinLogging
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VictronEssProcess(
    executorService: ExecutorService,
    private val victronService: VictronService,
    private val persistenceService: PersistenceService,
    private val energyPriceService: EnergyPriceService,
) : AbstractProcess(executorService, 5) {

    private val logger = KotlinLogging.logger { }

    private var currentProfile: ProfileSettings? = null
    private var essState: String = ""

    val listeners = ConcurrentHashMap<String, (data: ESSState) -> Unit>()

    override fun start() {
        super.start()
        victronService.listeners["VictronEssProcess"] = {
            listeners.values.forEach { l ->
                l(current())
            }
        }
    }

    fun getCurrentOperationMode() = OperationMode.valueOf(
        persistenceService["VictronEssProcess.currentOperationMode", "passthrough"]!!
    )
    fun setCurrentOperationMode(operationMode: OperationMode) {
        persistenceService["VictronEssProcess.currentOperationMode"] = operationMode.name
    }

    override fun tickLocked(): Boolean {
        val targetProfile = calculateProfile()

        if (targetProfile == null) {
            if (currentProfile != null) {
                victronService.essMode3_setAcPowerSetPointMode(null)
                currentProfile = null
                logger.info { "Switching to passthrough" }
            } else {
                logger.info { "Not writing to victron - passthrough" }
            }
            return true
        }

        val profileSettings = getProfile(targetProfile)

        val result = if (profileSettings.passthrough()) {
            null
        } else {
            val essValues = victronService.essValues
            calculateAcPowerSetPoints(
                VictronEssCalculationInput(
                    essValues.outputL1.power.toLong(),
                    essValues.outputL2.power.toLong(),
                    essValues.outputL3.power.toLong(),
                    profileSettings.acPowerSetPoint,
                    profileSettings.maxChargePower,
                    profileSettings.maxDischargePower,
                )
            )
        }

        logger.info { "Using targetProfile=$targetProfile profileSettings=$profileSettings result=$result" }
        victronService.essMode3_setAcPowerSetPointMode(result)
        currentProfile = profileSettings
        return true
    }

    private fun calculateProfile() = when (getCurrentOperationMode()) {
        OperationMode.passthrough -> {
            essState = "Passthrough mode"
            null
        }

        OperationMode.manual -> {
            essState = "Manual mode"
            ProfileType.manual
        }

        OperationMode.automatic -> {

            val suitablePrices = energyPriceService.findSuitablePrices(serviceEnergyStorage, LocalDate.now(zoneId))
            val priceDecision = suitablePrices.priceDecision()

            if (priceDecision == null) {
                essState = "No prices"
                null
            } else {
                essState = "${priceDecision.current} until ${priceDecision.changesAt.atZone(zoneId).toLocalTime()}"
                when (priceDecision.current) {
                    PriceCategory.expensive -> if (isSoCTooLow()) {
                        null
                    } else {
                        ProfileType.autoDischarging
                    }

                    PriceCategory.neutral -> null

                    PriceCategory.cheap -> if (isSoCTooHigh()) {
                        null
                    } else {
                        ProfileType.autoCharging
                    }
                }
            }
        }
    }

    private fun isSoCTooLow(): Boolean {
        val soc = victronService.current().soc.toInt()
        return soc <= getSoCLimit().from
    }

    private fun isSoCTooHigh(): Boolean {
        val soc = victronService.current().soc.toInt()
        return soc >= getSoCLimit().to
    }

    fun setSoCLimit(soCLimit: SoCLimit) {
        persistenceService["VictronEssProcess.socLimit.from"] = soCLimit.from.toString()
        persistenceService["VictronEssProcess.socLimit.to"] = soCLimit.to.toString()
    }

    fun getSoCLimit(): SoCLimit {
        return SoCLimit(
            persistenceService["VictronEssProcess.socLimit.from", "20"]!!.toInt(),
            persistenceService["VictronEssProcess.socLimit.to", "80"]!!.toInt(),
        )
    }

    override fun logger() = logger

    fun current() = ESSState(
        victronService.essValues,
        getCurrentOperationMode(),
        currentProfile?.profileType,
        getSoCLimit(),
        ProfileType.values().map { t -> getProfile(t) },
        essState
    )

    fun handleWrite(essWrite: ESSWrite) {
        if (essWrite.operationMode != null) {
            setCurrentOperationMode(essWrite.operationMode)
        }
        if (essWrite.soCLimit != null) {
            setSoCLimit(essWrite.soCLimit)
        }
        if (essWrite.updateProfile != null) {
            setProfile(essWrite.updateProfile)
        }
    }

    fun getProfile(profileType: ProfileType): ProfileSettings = ProfileSettings(
        profileType,
        persistenceService["VictronEssProcess.profiles.$profileType.acPowerSetPoint", "30000"]!!.toLong(),
        persistenceService["VictronEssProcess.profiles.$profileType.maxChargePower", "0"]!!.toLong(),
        persistenceService["VictronEssProcess.profiles.$profileType.maxDischargePower", "0"]!!.toLong(),
    )

    fun setProfile(profileSettings: ProfileSettings) {
        persistenceService["VictronEssProcess.profiles.${profileSettings.profileType}.acPowerSetPoint"] =
            profileSettings.acPowerSetPoint.toString()
        persistenceService["VictronEssProcess.profiles.${profileSettings.profileType}.maxChargePower"] =
            profileSettings.maxChargePower.toString()
        persistenceService["VictronEssProcess.profiles.${profileSettings.profileType}.maxDischargePower"] =
            profileSettings.maxDischargePower.toString()
    }

    enum class OperationMode {
        automatic,
        passthrough,
        manual
    }

    enum class ProfileType {
        autoCharging,
        autoDischarging,
        manual
    }

    data class ProfileSettings(
        val profileType: ProfileType,
        val acPowerSetPoint: Long = 0,
        val maxChargePower: Long = 0,
        val maxDischargePower: Long = 0,
    ) {
        fun passthrough(): Boolean = maxDischargePower == 0L && maxChargePower == 0L
    }
}

data class ESSState(
    val measurements: ESSValues,
    val operationMode: VictronEssProcess.OperationMode,
    val currentProfile: VictronEssProcess.ProfileType?,
    val soCLimit: SoCLimit,
    val profileSettings: List<VictronEssProcess.ProfileSettings>,
    val essState: String,
)

data class ESSWrite(
    val operationMode: VictronEssProcess.OperationMode?,
    val updateProfile: VictronEssProcess.ProfileSettings?,
    val soCLimit: SoCLimit?,
)

data class SoCLimit(
    val from: Int,
    val to: Int,
)

fun main() {
    val executorService = Executors.newCachedThreadPool()
    val objectMapper = jacksonObjectMapper().registerModule(kotlinModule())
    val persistenceService = PersistenceService(objectMapper)
    val influxDBClient = InfluxDBClient(persistenceService)
    val victronService = VictronService(
        "192.168.1.18",
        objectMapper,
        executorService,
        persistenceService,
        influxDBClient,
        EnergyConsumptionService(influxDBClient)
    )

    while (true) {
        val current = victronService.current()
        println("State: " + current.systemState)
        println("L1: IN=" + current.gridL1.power.toLong() + " OUT=" + current.outputL1.power.toLong())
        println("L2: IN=" + current.gridL2.power.toLong() + " OUT=" + current.outputL2.power.toLong())
        println("L3: IN=" + current.gridL3.power.toLong() + " OUT=" + current.outputL3.power.toLong())
        println("Battery: " + current.batteryPower)
        println("oldestUpdatedField: " + current.getOldestUpdatedField())

        try {
            victronService.essMode3_setAcPowerSetPointMode(
                VictronEssCalculation.VictronEssCalculationResult(
                    //current.outputL1.power.toLong(),
                    20000,
                    current.outputL2.power.toLong(),
                    current.outputL3.power.toLong(),
                )
            )
        } catch (e: Exception) {
            KotlinLogging.logger {}.warn(e) {  }
        }

        Thread.sleep(5000)
    }
}