package com.dehnes.smarthome.heating

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.energy_pricing.tibber.TibberService
import com.dehnes.smarthome.environment_sensors.FirmwareDataRequest
import com.dehnes.smarthome.environment_sensors.FirmwareHolder
import com.dehnes.smarthome.lora.LoRaConnection
import com.dehnes.smarthome.lora.LoRaInboundPacketDecrypted
import com.dehnes.smarthome.lora.LoRaPacketType
import com.dehnes.smarthome.lora.maxPayload
import com.dehnes.smarthome.utils.*
import mu.KotlinLogging
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import java.util.zip.CRC32
import kotlin.math.min

private const val TARGET_TEMP_KEY = "HeatingControllerService.targetTemp"
private const val HEATER_STATUS_KEY = "HeatingControllerService.heaterTarget"
private const val OPERATING_MODE = "HeatingControllerService.operatingMode"
private const val SKIPPERCENTEXPENSIVEHOURS_KEY = "HeatingControllerService.skipPercentExpensiveHours"

class UnderFloorHeaterService(
    private val loRaConnection: LoRaConnection,
    private val executorService: ExecutorService,
    private val persistenceService: PersistenceService,
    private val influxDBClient: InfluxDBClient,
    private val tibberService: TibberService,
    private val clock: Clock
) : AbstractProcess(executorService, 42) {

    private val loRaAddr = 18

    @Volatile
    private var keyId: Int = 1

    @Volatile
    private var firmwareHolder: FirmwareHolder? = null

    @Volatile
    private var firmwareDataRequest: FirmwareDataRequest? = null

    private val receiveQueue = LinkedBlockingQueue<LoRaInboundPacketDecrypted>()

    private val logger = KotlinLogging.logger { }

    val listeners = ConcurrentHashMap<String, (UnderFloorHeaterResponse) -> Unit>()

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
    }

    @Volatile
    private var lastStatus = UnderFloorHeaterStatus(
        UnderFloorHeaterMode.values().first { it.mode == getCurrentMode() },
        OnOff.off,
        getTargetTemperature(),
        getSkipPercentExpensiveHours(),
        null,
        0,
        null
    )

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

    fun startFirmwareUpgrade(firmwareBased64Encoded: String) = asLocked {
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

    fun adjustTime() = asLocked {
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

    private fun handleNewData(packet: LoRaInboundPacketDecrypted): Boolean {
        val currentMode: Mode = getCurrentMode()
        logger.info("Current mode: $currentMode")

        recordLocalValues(currentMode)
        val sensorData = parseAndRecord(packet, clock.millis())

        var waitUntilCheapHour: Instant? = null

        // evaluate state
        when (currentMode) {
            Mode.OFF -> persistenceService[HEATER_STATUS_KEY] = "off"
            Mode.ON -> persistenceService[HEATER_STATUS_KEY] = "on"
            Mode.MANUAL -> {
                if (sensorData.temperatureError > 0) {
                    logger.info("Forcing heater off due to temperature error=${sensorData.temperatureError}")
                    persistenceService[HEATER_STATUS_KEY] = "off"
                } else {
                    val targetTemperature = getTargetTemperature()
                    logger.info("Evaluating target temperature now: $targetTemperature")
                    waitUntilCheapHour = tibberService.mustWaitUntilV2(getSkipPercentExpensiveHours())
                    if (waitUntilCheapHour == null && sensorData.temperature < targetTemperature * 100) {
                        logger.info("Setting heater to on")
                        persistenceService[HEATER_STATUS_KEY] = "on"
                    } else {
                        logger.info {
                            "Setting heater to off. waitUntil=${waitUntilCheapHour?.atZone(clock.zone)}"
                        }
                        persistenceService[HEATER_STATUS_KEY] = "off"
                    }
                }
            }
        }

        var heaterStatus = sensorData.heaterIsOn

        // bring the heater to the desired state
        val executionResult = if (sensorData.heaterIsOn && "off" == getConfiguredHeaterTarget()) {
            if (sendOffCommand()) {
                heaterStatus = false
                true
            } else {
                false
            }
        } else if (!sensorData.heaterIsOn && "on" == getConfiguredHeaterTarget()) {
            if (sendOnCommand()) {
                heaterStatus = true
                true
            } else {
                false
            }
        } else {
            true
        }

        lastStatus = UnderFloorHeaterStatus(
            mode = UnderFloorHeaterMode.values().first { it.mode == currentMode },
            status = if (heaterStatus) OnOff.on else OnOff.off,
            targetTemperature = getTargetTemperature(),
            skipPercentExpensiveHours = getSkipPercentExpensiveHours(),
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
        executorService.submit {
            listeners.forEach {
                try {
                    it.value(getCurrentState())
                } catch (e: Exception) {
                    logger.error("", e)
                }
            }
        }
    }

    fun getCurrentState() = asLocked {
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

    fun updateMode(newMode: UnderFloorHeaterMode): Boolean {
        setCurrentMode(newMode.mode)
        return tick()
    }

    fun updateTargetTemperature(targetTemperature: Int): Boolean {
        check(targetTemperature in 10..50)
        setTargetTemperature(targetTemperature)
        lastStatus = lastStatus.copy(
            targetTemperature = targetTemperature
        )
        executorService.submit {
            tick()
        }
        return true
    }

    fun setEkipPercentExpensiveHours(skipPercentExpensiveHours: Int): Boolean {
        check(skipPercentExpensiveHours in 0..100)
        setSkipPercentExpensiveHours(skipPercentExpensiveHours)
        lastStatus = lastStatus.copy(
            skipPercentExpensiveHours = skipPercentExpensiveHours
        )
        executorService.submit {
            tick()
        }
        return true
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
        influxDBClient.recordSensorData(
            InfluxDBRecord(
                clock.instant(),
                "sensor",
                mapOf(
                    "manual_mode" to (if (currentMode == Mode.MANUAL) 1 else 0).toString(),
                    "target_temperature" to getTargetTemperature().toString(),
                    "configured_heater_target" to (if (getConfiguredHeaterTarget() == "on") 1 else 0).toString()
                ),
                mapOf(
                    "room" to "heating_controller"
                )
            )
        )
    }

    private fun sendOnCommand(): Boolean {
        var sent = false
        receiveQueue.clear()
        val ct = CountDownLatch(1)
        loRaConnection.send(
            keyId,
            loRaAddr,
            LoRaPacketType.HEATER_ON_REQUEST,
            byteArrayOf(),
            null
        ) {
            sent = it
            ct.countDown()
        }

        return sent && receiveQueue.poll(2, TimeUnit.SECONDS)?.type == LoRaPacketType.HEATER_RESPONSE
    }

    private fun sendOffCommand(): Boolean {
        var sent = false
        receiveQueue.clear()
        val ct = CountDownLatch(1)
        loRaConnection.send(
            keyId,
            loRaAddr,
            LoRaPacketType.HEATER_OFF_REQUEST,
            byteArrayOf(),
            null
        ) {
            sent = it
            ct.countDown()
        }

        return sent && receiveQueue.poll(2, TimeUnit.SECONDS)?.type == LoRaPacketType.HEATER_RESPONSE
    }

    private fun getCurrentMode() = Mode.valueOf(
        persistenceService[OPERATING_MODE, Mode.OFF.name]!!
    )

    private fun setCurrentMode(m: Mode) {
        persistenceService[OPERATING_MODE] = m.name
    }

    private fun getTargetTemperature() =
        Integer.valueOf(persistenceService[TARGET_TEMP_KEY, (25).toString()])

    private fun setTargetTemperature(t: Int) {
        persistenceService[TARGET_TEMP_KEY] = t.toString()
    }

    private fun getSkipPercentExpensiveHours() =
        Integer.valueOf(persistenceService[SKIPPERCENTEXPENSIVEHOURS_KEY, 40.toString()])

    private fun setSkipPercentExpensiveHours(t: Int) {
        persistenceService[SKIPPERCENTEXPENSIVEHOURS_KEY] = t.toString()
    }

    private fun getConfiguredHeaterTarget() = persistenceService[HEATER_STATUS_KEY, "off"]!!

}

data class TemperatureAndHeaterStatus(
    val temp: Int,
    val heaterIsOn: Boolean
)

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
        return "UnderFloorSensorData(temperature=${toTemperature()}, heaterIsOn=$heaterIsOn, receivedAt=$receivedAt, temperatureError=$temperatureError)"
    }

}