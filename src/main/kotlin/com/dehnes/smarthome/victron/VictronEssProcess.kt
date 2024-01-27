package com.dehnes.smarthome.victron

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.config.VictronEssProcessProfile
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.PriceCategory
import com.dehnes.smarthome.energy_pricing.priceDecision
import com.dehnes.smarthome.energy_pricing.serviceEnergyStorage
import com.dehnes.smarthome.users.SystemUser
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.AbstractProcess
import com.dehnes.smarthome.utils.DateTimeUtils.zoneId
import com.dehnes.smarthome.victron.VictronEssCalculation.VictronEssCalculationInput
import com.dehnes.smarthome.victron.VictronEssCalculation.calculateAcPowerSetPoints
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VictronEssProcess(
    executorService: ExecutorService,
    private val victronService: VictronService,
    private val configService: ConfigService,
    private val energyPriceService: EnergyPriceService,
    private val dalyBmsDataLogger: DalyBmsDataLogger,
    private val userSettingsService: UserSettingsService,
) : AbstractProcess(executorService, 5) {

    private val logger = KotlinLogging.logger { }

    private var currentProfile: ProfileSettings? = null
    private var essState: String = ""

    @Volatile
    private var bmsData = listOf<BmsData>()

    val listeners = ConcurrentHashMap<String, (data: ESSState) -> Unit>()

    override fun start() {
        super.start()
        victronService.listeners["VictronEssProcess"] = {
            listeners.values.forEach { l ->
                l(current(SystemUser))
            }
        }
        dalyBmsDataLogger.listeners["VictronEssProcess"] = {
            this.bmsData = it
        }
    }

    fun current(user: String?): ESSState {
        check(
            userSettingsService.canUserRead(user, UserRole.energyStorageSystem)
        ) { "User $user cannot read ESS state" }
        return ESSState(
            victronService.essValues,
            getCurrentOperationMode(),
            currentProfile?.profileType,
            getSoCLimit(),
            ProfileType.values().map { t -> getProfile(t) },
            essState,
            bmsData,
        )
    }

    fun handleWrite(user: String?, essWrite: ESSWrite) {
        check(
            userSettingsService.canUserWrite(user, UserRole.energyStorageSystem)
        ) { "User $user cannot read ESS state" }

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


    private fun getCurrentOperationMode() = configService.getVictronEssProcessSettings().currentOperationMode

    private fun setCurrentOperationMode(operationMode: OperationMode) {
        configService.setVictronEssProcessSettings(
            configService.getVictronEssProcessSettings().copy(
                currentOperationMode = operationMode
            )
        )
    }

    override fun tickLocked(): Boolean {
        val targetProfile = calculateProfile()

        if (targetProfile == null) {
            if (currentProfile != null) {
                victronService.essMode3_setAcPowerSetPointMode(null)
                currentProfile = null
                logger.info { "Switching to passthrough" }
            } else {
                logger.debug { "Not writing to victron - passthrough" }
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

        logger.debug { "Using targetProfile=$targetProfile profileSettings=$profileSettings result=$result" }
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
            if (isSoCTooLow()) {
                essState = "Manual mode (SoC too low)"
                null
            } else if (isSoCTooHigh()) {
                essState = "Manual mode (SoC too high)"
                null
            } else {
                essState = "Manual mode"
                ProfileType.manual
            }
        }

        OperationMode.automatic -> {
            val now = Instant.now()
            val onlineBmses = bmsData
                .filter { it.timestamp.plusSeconds(bmsAssumeDeadAfterSeconds()).isAfter(now) }

            if (onlineBmses.size < minNumberOfOnlineBmses()) {
                essState = "Not enough BMSes online: ${onlineBmses.joinToString(", ") { it.bmsId.displayName }}"
                null
            } else {
                val suitablePrices =
                    energyPriceService.findSuitablePrices(SystemUser, serviceEnergyStorage, LocalDate.now(zoneId))
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
    }

    private fun isSoCTooLow(): Boolean {
        val soc = victronService.current().soc.toInt()
        return soc <= getSoCLimit().from
    }

    private fun isSoCTooHigh(): Boolean {
        val soc = victronService.current().soc.toInt()
        return soc > getSoCLimit().to
    }

    private fun setSoCLimit(soCLimit: SoCLimit) {
        configService.setVictronEssProcessSettings(
            configService.getVictronEssProcessSettings().copy(
                socLimitFrom = soCLimit.from,
                socLimitTo = soCLimit.to
            )
        )
    }

    private fun getSoCLimit(): SoCLimit {
        val settings = configService.getVictronEssProcessSettings()
        return SoCLimit(
            settings.socLimitFrom,
            settings.socLimitTo,
        )
    }

    private fun bmsAssumeDeadAfterSeconds() =
        configService.getVictronEssProcessSettings().bmsAssumeDeadAfterSeconds.toLong()

    private fun minNumberOfOnlineBmses() = configService.getVictronEssProcessSettings().minNumberOfOnlineBmses

    override fun logger() = logger

    private fun getProfile(profileType: ProfileType): ProfileSettings {
        val profile = configService.getVictronEssProcessSettings().profiles[profileType.name]!!
        return ProfileSettings(
            profileType,
            profile.acPowerSetPoint,
            profile.maxChargePower,
            profile.maxDischargePower
        )
    }

    private fun setProfile(profileSettings: ProfileSettings) {
        val settings = configService.getVictronEssProcessSettings()
        configService.setVictronEssProcessSettings(
            settings.copy(
                profiles = settings.profiles + (profileSettings.profileType.name to VictronEssProcessProfile(
                    profileSettings.acPowerSetPoint,
                    profileSettings.maxChargePower,
                    profileSettings.maxDischargePower
                ))
            )
        )
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
    val bmsData: List<BmsData>
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
    val influxDBClient = InfluxDBClient(ConfigService(objectMapper))
    val victronService = VictronService(
        "192.168.1.18",
        objectMapper,
        executorService,
        ConfigService(objectMapper),
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
            KotlinLogging.logger {}.warn(e) { }
        }

        Thread.sleep(5000)
    }
}