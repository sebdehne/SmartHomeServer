package com.dehnes.smarthome.heating

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.energy_consumption.EnergyConsumptionService
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.energy_pricing.PriceCategory
import com.dehnes.smarthome.energy_pricing.priceDecision
import com.dehnes.smarthome.environment_sensors.FirmwareDataRequest
import com.dehnes.smarthome.environment_sensors.FirmwareHolder
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.lora.LoRaInboundPacketDecrypted
import com.dehnes.smarthome.lora.LoRaPacketType
import com.dehnes.smarthome.lora.maxPayload
import com.dehnes.smarthome.users.SystemUser
import com.dehnes.smarthome.users.UserRole
import com.dehnes.smarthome.users.UserSettingsService
import com.dehnes.smarthome.utils.*
import com.dehnes.smarthome.victron.VictronService
import mu.KotlinLogging
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.*
import java.util.zip.CRC32
import kotlin.math.min

class UnderFloorHeaterService(
    private val loRaConnection: LoRaConnection,
    private val executorService: ExecutorService,
    private val configService: ConfigService,
    private val influxDBClient: InfluxDBClient,
    private val energyPriceService: EnergyPriceService,
    private val clock: Clock,
    private val victronService: VictronService,
    private val energyConsumptionService: EnergyConsumptionService,
    private val userSettingsService: UserSettingsService,
) : AbstractProcess(executorService, 42) {

    private val loRaAddr = 18
    private val serviceType = "HeaterUnderFloor"

    @Volatile
    private var keyId: Int = 1

    @Volatile
    private var firmwareHolder: FirmwareHolder? = null

    @Volatile
    private var firmwareDataRequest: FirmwareDataRequest? = null

    private val receiveQueue = LinkedBlockingQueue<LoRaInboundPacketDecrypted>()

    private val logger = KotlinLogging.logger { }

    private val listeners = ConcurrentHashMap<String, (UnderFloorHeaterResponse) -> Unit>()
    var gridOK = true

    init {
        loRaConnection.listeners.add { packet ->
            if (packet.from != loRaAddr) {
                false
            } else {
                keyId = packet.keyId

                if (packet.type == LoRaPacketType.FIRMWARE_DATA_REQUEST) {
                    onFirmwareDataRequest(packet)
                } else {
                    receiveQueue.offer(packet)
                }

                true
            }
        }
        victronService.listeners["UnderFloorHeaterService"] = {
            val previousGridOk = gridOK
            gridOK = it.isGridOk()
            if (previousGridOk && !gridOK) {
                logger.warn { "Grid went offline, running control loop now" }
                tick()
            }
        }
    }

    @Volatile
    private var lastStatus = run {
        val settings = getSettings()
        UnderFloorHeaterStatus(
            UnderFloorHeaterMode.values().first { it.mode == settings.operatingMode },
            OnOff.off,
            settings.targetTemp,
            null,
            0,
            null
        )
    }

    override fun logger() = logger

    override fun tickLocked(): Boolean {
        var success = false

        val firmwareDataRequest = this.firmwareDataRequest
        if (firmwareDataRequest != null && firmwareDataRequest.receivedAt > clock.millis() - (30 * 1000)) {
            logger.info { "Not requesting data - in firmware upgrade" }
            success = true
        } else {
            this.firmwareDataRequest = null

            // request measurement
            var retryCount = 5
            while (--retryCount > 0) {
                var sent = false
                val ct = CountDownLatch(1)
                receiveQueue.clear()
                loRaConnection.send(
                    keyId,
                    loRaAddr,
                    LoRaPacketType.GARAGE_HEATER_DATA_REQUESTV2,
                    byteArrayOf(1),
                    null
                ) {
                    sent = it
                    ct.countDown()
                }
                ct.await(2, TimeUnit.SECONDS)

                if (sent) {
                    logger.info { "Sent data request" }
                    val dataResponse = receiveQueue.poll(2, TimeUnit.SECONDS)
                    if (dataResponse != null) {
                        handleNewData(dataResponse)
                        success = true
                        break
                    }
                } else {
                    logger.info { "Could not send data request" }
                }

                Thread.sleep(1000)
            }
        }


        return success
    }

    fun addListener(user: String?, id: String, l: (UnderFloorHeaterResponse) -> Unit) {
        check(
            userSettingsService.canUserRead(user, UserRole.heaterUnderFloor)
        ) { "User=$user cannot read underFloorHeater" }
        listeners[id] = l
    }

    fun removeListener(id: String) {
        listeners.remove(id)
    }

    fun startFirmwareUpgrade(user: String?, firmwareBased64Encoded: String) = asLocked {
        check(
            userSettingsService.canUserAdmin(user, UserRole.heaterUnderFloor)
        ) { "User=$user cannot admin underFloorHeater" }
        val firmwareHolder = FirmwareHolder(
            "unknown.file.name",
            Base64.getDecoder().decode(firmwareBased64Encoded)
        )
        this.firmwareHolder = firmwareHolder

        val byteArray = ByteArray(8) { 0 }

        // totalLength: 4 bytes
        System.arraycopy(
            (firmwareHolder.data.size).to32Bit(),
            0,
            byteArray,
            0,
            4
        )

        // crc32: 4 bytes
        val crc32 = CRC32()
        crc32.update(firmwareHolder.data)
        val crc32Value = crc32.value
        System.arraycopy(
            crc32Value.to32Bit(),
            0,
            byteArray,
            4,
            4
        )

        var sent = false
        val ct = CountDownLatch(1)
        receiveQueue.clear()
        loRaConnection.send(
            keyId,
            loRaAddr,
            LoRaPacketType.FIRMWARE_INFO_RESPONSE,
            byteArray,
            null
        ) {
            ct.countDown()
            sent = it
        }
        ct.await(2, TimeUnit.SECONDS)

        sent
    } ?: false

    fun adjustTime(user: String?) = asLocked {
        check(
            userSettingsService.canUserAdmin(user, UserRole.heaterUnderFloor)
        ) { "User=$user cannot admin underFloorHeater" }

        val ct = CountDownLatch(1)
        receiveQueue.clear()
        loRaConnection.send(
            keyId,
            loRaAddr,
            LoRaPacketType.ADJUST_TIME_REQUEST,
            byteArrayOf(),
            null
        ) {
            ct.countDown()
        }
        ct.await(2, TimeUnit.SECONDS)
        receiveQueue.poll(2, TimeUnit.SECONDS)?.type == LoRaPacketType.ADJUST_TIME_RESPONSE
    } ?: false

    fun getCurrentState(user: String?) = asLocked {
        check(
            userSettingsService.canUserRead(user, UserRole.heaterUnderFloor)
        ) { "User=$user cannot read underFloorHeater" }

        firmwareDataRequest?.let {
            UnderFloorHeaterResponse(
                firmwareUpgradeState = FirmwareUpgradeState(
                    firmwareHolder?.data?.size ?: 0,
                    it.offset,
                    it.timestampDelta,
                    it.receivedAt,
                    it.rssi
                )
            )
        } ?: UnderFloorHeaterResponse(lastStatus)
    }!!

    fun updateMode(user: String?, newMode: UnderFloorHeaterMode): Boolean {
        check(
            userSettingsService.canUserWrite(user, UserRole.heaterUnderFloor)
        ) { "User=$user cannot write underFloorHeater" }
        setCurrentMode(newMode.mode)
        return tick()
    }

    fun updateTargetTemperature(user: String?, targetTemperature: Int): Boolean {
        check(
            userSettingsService.canUserWrite(user, UserRole.heaterUnderFloor)
        ) { "User=$user cannot write underFloorHeater" }
        check(targetTemperature in 10..50)
        setTargetTemperature(targetTemperature)
        lastStatus = lastStatus.copy(
            targetTemperature = targetTemperature
        )
        executorService.submit(withLogging {
            tick()
        })
        return true
    }

    private fun onFirmwareDataRequest(packet: LoRaInboundPacketDecrypted) {

        val firmwareDataRequest = FirmwareDataRequest(
            readInt32Bits(packet.payload, 0),
            readInt32Bits(packet.payload, 4),
            packet.payload[8].toUnsignedInt(),
            packet.timestampDelta,
            clock.millis(),
            packet.rssi
        )

        logger.info { "Handling firmwareDataRequest from ${packet.from} - $firmwareDataRequest" }

        firmwareHolder?.let {

            if (firmwareDataRequest.offset > it.data.size) {
                error("Requested offset too large")
            }

            var bytesSent = 0
            var sequentNumber = 0
            while (bytesSent < firmwareDataRequest.length) {
                val nextChunkSize = min(
                    min(
                        maxPayload - 1, // sequentNumber 1 byte
                        firmwareDataRequest.maxBytesPerResponse
                    ),
                    firmwareDataRequest.length - bytesSent
                )

                val data = ByteArray(nextChunkSize + 1)

                // write sequentNumber
                data[0] = sequentNumber.toByte()

                // write firmware bytes
                System.arraycopy(
                    it.data,
                    firmwareDataRequest.offset + bytesSent,
                    data,
                    1, // after sequentNumber
                    nextChunkSize
                )

                // send chunk
                loRaConnection.send(packet.keyId, packet.from, LoRaPacketType.FIRMWARE_DATA_RESPONSE, data, null) { }

                bytesSent += nextChunkSize
                sequentNumber++
            }

        } ?: logger.error { "No firmware present" }

        this.firmwareDataRequest = firmwareDataRequest
        onStatusChanged()
    }

    private fun getSettings() = configService.getHeaterSettings()
    private fun setHeaterTarget(target: OnOff) {
        configService.setHeaterSettings(
            getSettings().copy(
                heaterTarget = target
            )
        )
    }

    private fun handleNewData(packet: LoRaInboundPacketDecrypted): Boolean {
        val settings = getSettings()
        val currentMode = settings.operatingMode
        logger.info("Current mode: $currentMode")

        recordLocalValues(currentMode)
        val sensorData = parseAndRecord(packet, clock.millis())

        var waitUntilCheapHour: Instant? = null

        // evaluate state
        when (currentMode) {
            Mode.OFF -> setHeaterTarget(OnOff.off)
            Mode.ON -> {
                if (victronService.isGridOk()) {
                    setHeaterTarget(OnOff.on)
                } else {
                    setHeaterTarget(OnOff.off)
                }
            }

            Mode.MANUAL -> {
                if (sensorData.temperatureError > 0) {
                    logger.info("Forcing heater off due to temperature error=${sensorData.temperatureError}")
                    setHeaterTarget(OnOff.off)
                } else {
                    val targetTemperature = settings.targetTemp
                    logger.info("Evaluating target temperature now: $targetTemperature")

                    val suitablePrices =
                        energyPriceService.findSuitablePrices(
                            SystemUser,
                            serviceType,
                            LocalDate.now(DateTimeUtils.zoneId)
                        )
                    val priceDecision = suitablePrices.priceDecision()

                    if (!victronService.isGridOk()) {
                        waitUntilCheapHour = Instant.MAX
                    }
                    if (priceDecision?.current == PriceCategory.cheap && sensorData.temperature < targetTemperature * 100) {
                        logger.info("Setting heater to on. priceDecision=$priceDecision")
                        setHeaterTarget(OnOff.on)
                    } else {
                        waitUntilCheapHour = priceDecision?.changesAt
                        logger.info {
                            "Setting heater to off. waitUntil=${waitUntilCheapHour?.atZone(clock.zone)}"
                        }
                        setHeaterTarget(OnOff.off)
                    }
                }
            }
        }

        var heaterStatus = sensorData.heaterIsOn

        // bring the heater to the desired state
        val executionResult = if (sensorData.heaterIsOn && settings.heaterTarget == OnOff.off) {
            if (sendCommand(LoRaPacketType.HEATER_OFF_REQUEST)) {
                heaterStatus = false
                true
            } else {
                logger.warn { "No response" }
                false
            }
        } else if (!sensorData.heaterIsOn && settings.heaterTarget == OnOff.on) {
            if (sendCommand(LoRaPacketType.HEATER_ON_REQUEST)) {
                heaterStatus = true
                true
            } else {
                logger.warn { "No response" }
                false
            }
        } else {
            logger.info { "Nothing to do, heater already in desired state" }
            true
        }

        energyConsumptionService.reportPower(
            serviceType,
            (if (heaterStatus) 5000 else 0).toDouble()
        )

        lastStatus = UnderFloorHeaterStatus(
            mode = UnderFloorHeaterMode.values().first { it.mode == currentMode },
            status = if (heaterStatus) OnOff.on else OnOff.off,
            targetTemperature = settings.targetTemp,
            waitUntilCheapHour = waitUntilCheapHour?.toEpochMilli(),
            timestampDelta = sensorData.timestampDelta,
            fromController = UnderFloorHeaterStatusFromController(
                clock.millis(),
                sensorData.temperature,
                sensorData.temperatureError
            )
        )

        onStatusChanged()

        return executionResult
    }

    private fun onStatusChanged() {
        executorService.submit(withLogging {
            listeners.forEach {
                it.value(getCurrentState(SystemUser))
            }
        })
    }

    private fun parseAndRecord(
        packet: LoRaInboundPacketDecrypted,
        now: Long
    ): UnderFloorSensorData {
        val sensorData = UnderFloorSensorData.fromRfPacket(packet, now)

        logger.info { "Received sensorData=$sensorData" }

        influxDBClient.recordSensorData(
            InfluxDBRecord(
                Instant.ofEpochMilli(sensorData.receivedAt),
                "sensor",
                mapOf(
                    "temperature" to sensorData.toTemperature(),
                    "temperatureError" to sensorData.temperatureError.toString(),
                    "heater_status" to (if (sensorData.heaterIsOn) 1 else 0).toString(),
                ),
                mapOf(
                    "room" to "heating_controller"
                )
            )
        )

        return sensorData
    }

    private fun recordLocalValues(
        currentMode: Mode
    ) {
        val settings = getSettings()
        influxDBClient.recordSensorData(
            InfluxDBRecord(
                clock.instant(),
                "sensor",
                mapOf(
                    "manual_mode" to (if (currentMode == Mode.MANUAL) 1 else 0).toString(),
                    "target_temperature" to settings.targetTemp.toString(),
                    "configured_heater_target" to (if (settings.heaterTarget == OnOff.on) 1 else 0).toString()
                ),
                mapOf(
                    "room" to "heating_controller"
                )
            )
        )
    }

    private fun sendCommand(cmd: LoRaPacketType): Boolean {
        receiveQueue.clear()
        val ct = CountDownLatch(1)
        loRaConnection.send(
            keyId,
            loRaAddr,
            cmd,
            byteArrayOf(),
            null
        ) {
            ct.countDown()
        }

        return ct.await(10, TimeUnit.SECONDS) && receiveQueue.poll(
            2,
            TimeUnit.SECONDS
        )?.type == LoRaPacketType.HEATER_RESPONSE
    }

    private fun setCurrentMode(m: Mode) {
        configService.setHeaterSettings(
            getSettings().copy(
                operatingMode = m
            )
        )
    }

    private fun setTargetTemperature(t: Int) {
        configService.setHeaterSettings(
            getSettings().copy(
                targetTemp = t
            )
        )
    }

}

data class UnderFloorSensorData(
    val temperature: Int,
    val temperatureError: Int,
    val heaterIsOn: Boolean,
    val timestampDelta: Long,
    val receivedAt: Long
) {
    companion object {
        fun fromRfPacket(loRaInboundPacketDecrypted: LoRaInboundPacketDecrypted, now: Long): UnderFloorSensorData {
            val allocate = ByteBuffer.allocate(4)
            allocate.put(loRaInboundPacketDecrypted.payload[0])
            allocate.put(loRaInboundPacketDecrypted.payload[1])
            allocate.put(loRaInboundPacketDecrypted.payload[2])
            allocate.put(loRaInboundPacketDecrypted.payload[3])
            allocate.flip()
            val temperatureRaw = allocate.int
            val temperature = temperatureRaw.toFloat() / 16F
            val heaterStatus = loRaInboundPacketDecrypted.payload[7].toInt() > 0
            val temperatureError =
                if (loRaInboundPacketDecrypted.type == LoRaPacketType.GARAGE_HEATER_DATA_RESPONSEV2) {
                    loRaInboundPacketDecrypted.payload[9].toInt()
                } else {
                    0
                }


            return UnderFloorSensorData(
                (temperature * 100).toInt(),
                temperatureError,
                heaterStatus,
                loRaInboundPacketDecrypted.timestampDelta,
                now
            )
        }
    }

    fun toTemperature() = if (temperatureError > 0) "0" else (temperature.toFloat() / 100).toString()

    override fun toString(): String {
        return "UnderFloorSensorData(temperature=${toTemperature()}, heaterIsOn=$heaterIsOn, receivedAt=$receivedAt, temperatureError=$temperatureError, timestampDelta=$timestampDelta)"
    }

}