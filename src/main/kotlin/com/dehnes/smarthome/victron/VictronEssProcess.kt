package com.dehnes.smarthome.victron

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.HvakosterstrommenClient
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.PersistenceService
import com.dehnes.smarthome.victron.VictronEssCalculation.VictronEssCalculationInput
import com.dehnes.smarthome.victron.VictronEssCalculation.calculateAcPowerSetPoints
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import mu.KotlinLogging
import java.time.Clock
import java.time.ZoneId
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

    private var currentOperationMode = OperationMode.passthrough
    private var currentProfile: ProfileSettings? = null

    val listeners = ConcurrentHashMap<String, (data: ESSState) -> Unit>()

    override fun start() {
        super.start()
        victronService.listeners["VictronEssProcess"] = {
            listeners.values.forEach { l ->
                l(current())
            }
        }
    }

    override fun tickLocked(): Boolean {
        val targetProfile = calculateProfile()

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

    private fun calculateProfile(): ProfileType {
        val targetProfile = when (currentOperationMode) {
            OperationMode.passthrough -> ProfileType.passthrough
            OperationMode.manual -> ProfileType.manual
            OperationMode.automatic -> {
                if (isSoCTooLow()) {
                    if (energyPricesAreCheap()) {
                        ProfileType.autoCharging
                    } else {
                        ProfileType.passthrough
                    }
                } else if (isSoCTooHigh()) {
                    if (!energyPricesAreCheap()) {
                        ProfileType.autoDischarging
                    } else {
                        ProfileType.passthrough
                    }
                } else {
                    if (energyPricesAreCheap()) {
                        ProfileType.autoCharging
                    } else {
                        ProfileType.autoDischarging
                    }
                }
            }
        }

        return targetProfile
    }

    private fun energyPricesAreCheap() = energyPriceService.mustWaitUntilV2("EnergyStorage") == null

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

    fun currentOperationMode() =
        persistenceService["VictronEssProcess.operationMode", OperationMode.passthrough.name]!!.let { mode ->
            OperationMode.values().first { it.name == mode }
        }

    fun current() = ESSState(
        victronService.essValues,
        currentOperationMode,
        currentProfile?.profileType ?: calculateProfile(),
        getSoCLimit(),
        ProfileType.values().map { t ->
            getProfile(t)
        }
    )

    fun handleWrite(essWrite: ESSWrite) {
        if (essWrite.operationMode != null) {
            currentOperationMode = essWrite.operationMode
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
        passthrough,
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
    val currentProfile: VictronEssProcess.ProfileType,
    val soCLimit: SoCLimit,
    val profileSettings: List<VictronEssProcess.ProfileSettings>
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
    val victronService = VictronService("192.168.1.18", objectMapper, executorService, persistenceService)
    val energyPriceService = EnergyPriceService(
        Clock.system(ZoneId.of("Europe/Oslo")),
        objectMapper,
        HvakosterstrommenClient(objectMapper),
        InfluxDBClient(persistenceService, objectMapper),
        executorService,
        persistenceService
    )
    val victronEssProcess = VictronEssProcess(executorService, victronService, persistenceService, energyPriceService)
    victronEssProcess.start()

    while (true) {
        Thread.sleep(5000)
        val current = victronService.current()
        println("State: " + current.systemState)
        println("gridPower: " + current.gridPower)
        println("batteryPower: " + current.batteryPower)
        println("outputPower: " + current.outputPower)
        val current1 = victronEssProcess.current()
        println("currentProfile: " + current1.currentProfile)
        println("operationMode: " + current1.operationMode)
    }
}