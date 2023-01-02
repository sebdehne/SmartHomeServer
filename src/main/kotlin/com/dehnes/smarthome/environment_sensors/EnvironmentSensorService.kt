package com.dehnes.smarthome.environment_sensors

import com.dehnes.smarthome.api.dtos.*
import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.datalogging.InfluxDBRecord
import com.dehnes.smarthome.lora.*
import com.dehnes.smarthome.lora.LoRaPacketType.*
import com.dehnes.smarthome.utils.*
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.zip.CRC32
import kotlin.math.max
import kotlin.math.min

class EnvironmentSensorService(
    private val loRaConnection: LoRaConnection,
    private val clock: Clock,
    private val executorService: ExecutorService,
    private val configService: ConfigService,
    private val influxDBClient: InfluxDBClient
) {

    val listeners = ConcurrentHashMap<String, (EnvironmentSensorEvent) -> Unit>()
    val outsideSensorIds = listOf(4, 5)

    private val logger = KotlinLogging.logger { }
    private val sensors = ConcurrentHashMap<Int, SensorState>()

    @Volatile
    private var firmwareHolder: FirmwareHolder? = null

    init {
        loRaConnection.listeners.add { packet ->
            synchronized(this) {
                val currentState = sensors[packet.from]

                val updatedState = when (packet.type) {
                    SENSOR_DATA_REQUEST -> onSensorData(currentState, packet)
                    SENSOR_DATA_REQUEST_V2 -> onSensorDataV2(currentState, packet)
                    SENSOR_DATA_REQUEST_V3 -> onSensorDataV3(currentState, packet)
                    FIRMWARE_INFO_REQUEST -> if (currentState == null) null else onFirmwareInfoRequest(
                        currentState,
                        packet
                    )

                    FIRMWARE_DATA_REQUEST -> if (currentState == null) null else onFirmwareDataRequest(
                        currentState,
                        packet
                    )

                    else -> null
                }

                if (updatedState != null) {
                    sensors[updatedState.id] = updatedState

                    // notify listeners
                    executorService.submit(withLogging {
                        listeners.forEach { (_, fn) ->
                            fn(
                                EnvironmentSensorEvent(
                                    EnvironmentSensorEventType.update,
                                    getAllState(),
                                    getFirmwareInfo()
                                )
                            )
                        }
                    })

                    true
                } else {
                    false
                }
            }
        }
    }

    fun getEnvironmentSensorResponse() = EnvironmentSensorResponse(
        getAllState(),
        getFirmwareInfo()
    )

    private fun getFirmwareInfo() = firmwareHolder?.let {
        FirmwareInfo(
            it.filename,
            it.data.size
        )
    }

    private fun getAllState() = sensors.map { (sensorId, sensorState) ->
        val lastReceivedMessage = sensorState.lastReceivedMessage
        val firmwareUpgradeState = firmwareHolder?.let {
            when (lastReceivedMessage) {
                is FirmwareInfoRequest -> FirmwareUpgradeState(
                    it.data.size,
                    0,
                    lastReceivedMessage.timestampDelta,
                    lastReceivedMessage.receivedAt,
                    lastReceivedMessage.rssi
                )

                is FirmwareDataRequest -> FirmwareUpgradeState(
                    it.data.size,
                    lastReceivedMessage.offset,
                    lastReceivedMessage.timestampDelta,
                    lastReceivedMessage.receivedAt,
                    lastReceivedMessage.rssi
                )

                else -> null
            }
        }

        val sensor = getSensor(sensorId)

        EnvironmentSensorState(
            sensorId,
            sensor.displayName,
            sensor.sleepTimeInSeconds,
            if (lastReceivedMessage is SensorData) EnvironmentSensorData(
                lastReceivedMessage.temperature,
                lastReceivedMessage.temperatureError,
                lastReceivedMessage.humidity,
                batteryAdcToMv(sensorId, lastReceivedMessage.adcBattery),
                lastReceivedMessage.adcLight,
                lastReceivedMessage.sleepTimeInSeconds,
                lastReceivedMessage.timestampDelta,
                lastReceivedMessage.receivedAt,
                lastReceivedMessage.rssi
            ) else null,
            firmwareUpgradeState,
            sensorState.firmwareVersion,
            sensorState.triggerFirmwareUpdate,
            sensorState.triggerReset,
            sensorState.triggerTimeAdjustment
        )
    }

    private fun getSensor(sensorId: Int) =
        configService.getEnvironmentSensors().sensors.entries.firstOrNull { it.value.id == sensorId }?.value
            ?: error("Fant ingen sensor med sensorId=$sensorId")

    private fun batteryAdcToMv(sensorId: Int, adcBattery: Long): Long {
        val fiveVadc = getSensor(sensorId).fiveVoltADC
        val slope = (5000 * 1000) / fiveVadc
        return (adcBattery * slope) / 1000
    }

    fun adjustSleepTimeInSeconds(sensorId: Int, sleepTimeInSecondsDelta: Int) = synchronized(this) {
        val sensorState = sensors[sensorId]
        if (sensorState == null) {
            false
        } else {
            val newSleepTimeInSeconds = min(
                max(
                    1,
                    getSensor(sensorId).sleepTimeInSeconds + sleepTimeInSecondsDelta
                ),
                10 * 60
            )
            setSleepTimeInSeconds(sensorId, newSleepTimeInSeconds)
            true
        }
    }

    fun firmwareUpgrade(sensorId: Int, scheduled: Boolean) = synchronized(this) {
        val sensorState = sensors[sensorId]
        if (sensorState == null) {
            false
        } else {
            sensors[sensorId] = sensorState.copy(
                triggerFirmwareUpdate = scheduled
            )
            true
        }
    }

    fun timeAdjustment(sensorId: Int?, scheduled: Boolean) = synchronized(this) {
        val selectedSensors = sensorId?.let { listOfNotNull(sensors[it]) } ?: sensors.values

        var adjustedSome = false
        selectedSensors.forEach {
            sensors[it.id] = it.copy(
                triggerTimeAdjustment = scheduled
            )
            adjustedSome = true
        }

        adjustedSome
    }

    fun configureReset(sensorId: Int?, scheduled: Boolean) = synchronized(this) {
        val selectedSensors = sensorId?.let { listOfNotNull(sensors[it]) } ?: sensors.values

        var adjustedSome = false
        selectedSensors.forEach {
            sensors[it.id] = it.copy(
                triggerReset = scheduled
            )
            adjustedSome = true
        }

        adjustedSome
    }

    fun setFirmware(filename: String, firmwareBased64Encoded: String) {
        synchronized(this) {
            firmwareHolder = FirmwareHolder(
                filename,
                Base64.getDecoder().decode(firmwareBased64Encoded)
            )
        }
    }

    private fun onFirmwareDataRequest(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {

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
                loRaConnection.send(
                    packet.keyId,
                    packet.from,
                    FIRMWARE_DATA_RESPONSE,
                    data,
                    if (configService.getEnvironmentSensors().validateTimestamp) null else packet.timestampSecondsSince2000
                ) { }

                bytesSent += nextChunkSize
                sequentNumber++
            }

        } ?: logger.error { "No firmware present" }

        return existingState.copy(lastReceivedMessage = firmwareDataRequest)
    }

    private fun onFirmwareInfoRequest(existingState: SensorState, packet: LoRaInboundPacketDecrypted): SensorState {
        logger.info { "Handling firmwareInfoRequest from ${packet.from}" }

        val byteArray = ByteArray(8) { 0 }
        firmwareHolder?.let {

            // totalLength: 4 bytes
            System.arraycopy(
                (it.data.size).to32Bit(),
                0,
                byteArray,
                0,
                4
            )

            // crc32: 4 bytes
            val crc32 = CRC32()
            crc32.update(it.data)
            val crc32Value = crc32.value
            System.arraycopy(
                crc32Value.to32Bit(),
                0,
                byteArray,
                4,
                4
            )
        }

        loRaConnection.send(
            packet.keyId,
            packet.from,
            FIRMWARE_INFO_RESPONSE,
            byteArray,
            if (configService.getEnvironmentSensors().validateTimestamp) null else packet.timestampSecondsSince2000
        ) {
            if (!it) {
                logger.info { "Could not send firmware-info-response" }
            }
        }

        return existingState.copy(
            triggerFirmwareUpdate = false,
            lastReceivedMessage = FirmwareInfoRequest(
                clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
                clock.millis(),
                packet.rssi
            )
        )
    }

    private fun onSensorData(existingState: SensorState?, packet: LoRaInboundPacketDecrypted): SensorState? {
        if (packet.payload.size != 21) {
            logger.warn { "Forced to ignore packet with invalid payloadSize=${packet.payload.size} != 21" }
            return existingState
        }
        val sensor = getSensor(packet.from)
        if (sensor.ignore) {
            logger.warn { "Ignoring $packet" }
            return existingState
        }
        val sensorData = SensorData(
            readLong32Bits(packet.payload, 0),
            false,
            readLong32Bits(packet.payload, 4),
            readLong32Bits(packet.payload, 8),
            readLong32Bits(packet.payload, 12),
            readLong32Bits(packet.payload, 16).toInt(),
            packet.payload[20].toInt(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis(),
            packet.rssi
        )
        logger.info { "Handling sensorData from ${packet.from}: $sensorData" }

        val responsePayload = ByteArray(6) { 0 }
        responsePayload[0] = (existingState?.triggerFirmwareUpdate ?: false).toInt().toByte()
        responsePayload[1] = (existingState?.triggerTimeAdjustment ?: false).toInt().toByte()
        val sleepTimeInSeconds = sensor.sleepTimeInSeconds
        val updatedSensorData = if (sensorData.sleepTimeInSeconds != sleepTimeInSeconds) {
            logger.info { "Sending updated sleep time. ${sensorData.sleepTimeInSeconds} -> $sleepTimeInSeconds" }
            System.arraycopy(
                sleepTimeInSeconds.to32Bit(),
                0,
                responsePayload,
                2,
                4
            )
            sensorData.copy(
                sleepTimeInSeconds = sleepTimeInSeconds
            )
        } else {
            sensorData
        }

        loRaConnection.send(
            packet.keyId,
            packet.from,
            SENSOR_DATA_RESPONSE,
            responsePayload,
            if (configService.getEnvironmentSensors().validateTimestamp) null else packet.timestampSecondsSince2000
        ) {
            if (!it) {
                logger.info { "Could not send sensor-data response" }
            }
        }

        influxDBClient.recordSensorData(
            updatedSensorData.toInfluxDbRecord(
                sensor.name,
                batteryAdcToMv(
                    packet.from,
                    updatedSensorData.adcBattery
                ),
                packet.from == 10 // no light sensor for #10 - "bak knevegg"
            )
        )

        if (packet.from in outsideSensorIds) {
            // update combined as well
            val outsideData = sensors
                .filter { it.key in outsideSensorIds }
                .filterNot { it.key == packet.from }
                .filter { it.value.lastReceivedMessage is SensorData }
                .map { it.value.lastReceivedMessage as SensorData } + updatedSensorData

            influxDBClient.recordSensorData(
                InfluxDBRecord(
                    Instant.ofEpochMilli(updatedSensorData.receivedAt),
                    "sensor",
                    mapOf(
                        "temperature" to ((outsideData.minOf { it.temperature }).toFloat() / 100).toString(),
                        "humidity" to ((outsideData.maxOf { it.humidity }).toFloat() / 100).toString()
                    ),
                    mapOf(
                        "room" to "outside_combined"
                    )
                )
            )
        }

        return SensorState(
            id = packet.from,
            firmwareVersion = sensorData.firmwareVersion,
            triggerTimeAdjustment = false,
            triggerFirmwareUpdate = false,
            triggerReset = false,
            lastReceivedMessage = updatedSensorData
        )
    }

    private fun onSensorDataV2(existingState: SensorState?, packet: LoRaInboundPacketDecrypted): SensorState? {
        if (packet.payload.size != 21) {
            logger.warn { "Forced to ignore packet with invalid payloadSize=${packet.payload.size} != 21" }
            return existingState
        }
        val sensor = getSensor(packet.from)
        if (sensor.ignore) {
            logger.warn { "Ignoring $packet" }
            return existingState
        }
        val sensorData = SensorData(
            readLong32Bits(packet.payload, 0),
            false,
            readLong32Bits(packet.payload, 4),
            readLong32Bits(packet.payload, 8),
            readLong32Bits(packet.payload, 12),
            readLong32Bits(packet.payload, 16).toInt(),
            packet.payload[20].toInt(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis(),
            packet.rssi
        )
        logger.info { "Handling sensorData from ${packet.from}: $sensorData" }

        val responsePayload = ByteArray(7) { 0 }
        responsePayload[0] = (existingState?.triggerFirmwareUpdate ?: false).toInt().toByte()
        responsePayload[1] = (existingState?.triggerTimeAdjustment ?: false).toInt().toByte()
        responsePayload[2] = (existingState?.triggerReset ?: false).toInt().toByte()
        val sleepTimeInSeconds = sensor.sleepTimeInSeconds
        val updatedSensorData = if (sensorData.sleepTimeInSeconds != sleepTimeInSeconds) {
            logger.info { "Sending updated sleep time. ${sensorData.sleepTimeInSeconds} -> $sleepTimeInSeconds" }
            System.arraycopy(
                sleepTimeInSeconds.to32Bit(),
                0,
                responsePayload,
                3,
                4
            )
            sensorData.copy(
                sleepTimeInSeconds = sleepTimeInSeconds
            )
        } else {
            sensorData
        }

        loRaConnection.send(
            packet.keyId,
            packet.from,
            SENSOR_DATA_RESPONSE_V2,
            responsePayload,
            if (configService.getEnvironmentSensors().validateTimestamp) null else packet.timestampSecondsSince2000
        ) {
            if (!it) {
                logger.info { "Could not send sensor-data response" }
            }
        }

        influxDBClient.recordSensorData(
            updatedSensorData.toInfluxDbRecord(
                sensor.name,
                batteryAdcToMv(
                    packet.from,
                    updatedSensorData.adcBattery
                ),
                packet.from == 10 // no light sensor for #10 - "bak knevegg"
            )
        )

        if (packet.from in outsideSensorIds) {
            // update combined as well
            val outsideData = sensors
                .filter { it.key in outsideSensorIds }
                .filterNot { it.key == packet.from }
                .filter { it.value.lastReceivedMessage is SensorData }
                .map { it.value.lastReceivedMessage as SensorData } + updatedSensorData

            influxDBClient.recordSensorData(
                InfluxDBRecord(
                    Instant.ofEpochMilli(updatedSensorData.receivedAt),
                    "sensor",
                    mapOf(
                        "temperature" to ((outsideData.minOf { it.temperature }).toFloat() / 100).toString(),
                        "humidity" to ((outsideData.maxOf { it.humidity }).toFloat() / 100).toString()
                    ),
                    mapOf(
                        "room" to "outside_combined"
                    )
                )
            )
        }

        return SensorState(
            id = packet.from,
            firmwareVersion = sensorData.firmwareVersion,
            triggerTimeAdjustment = false,
            triggerFirmwareUpdate = false,
            triggerReset = false,
            lastReceivedMessage = updatedSensorData
        )
    }

    private fun onSensorDataV3(existingState: SensorState?, packet: LoRaInboundPacketDecrypted): SensorState? {
        if (packet.payload.size != 22) {
            logger.warn { "Forced to ignore packet with invalid payloadSize=${packet.payload.size} != 22" }
            return existingState
        }
        val sensor = getSensor(packet.from)
        if (sensor.ignore) {
            logger.warn { "Ignoring $packet" }
            return existingState
        }
        val sensorData = SensorData(
            readLong32Bits(packet.payload, 0),
            packet.payload[21].toInt() > 0,
            readLong32Bits(packet.payload, 4),
            readLong32Bits(packet.payload, 8),
            readLong32Bits(packet.payload, 12),
            readLong32Bits(packet.payload, 16).toInt(),
            packet.payload[20].toInt(),
            clock.timestampSecondsSince2000() - packet.timestampSecondsSince2000,
            clock.millis(),
            packet.rssi
        )
        logger.info { "Handling sensorData from ${packet.from}: $sensorData" }

        val responsePayload = ByteArray(7) { 0 }
        responsePayload[0] = (existingState?.triggerFirmwareUpdate ?: false).toInt().toByte()
        responsePayload[1] = (existingState?.triggerTimeAdjustment ?: false).toInt().toByte()
        responsePayload[2] = (existingState?.triggerReset ?: false).toInt().toByte()
        val sleepTimeInSeconds = sensor.sleepTimeInSeconds
        val updatedSensorData = if (sensorData.sleepTimeInSeconds != sleepTimeInSeconds) {
            logger.info { "Sending updated sleep time. ${sensorData.sleepTimeInSeconds} -> $sleepTimeInSeconds" }
            System.arraycopy(
                sleepTimeInSeconds.to32Bit(),
                0,
                responsePayload,
                3,
                4
            )
            sensorData.copy(
                sleepTimeInSeconds = sleepTimeInSeconds
            )
        } else {
            sensorData
        }

        loRaConnection.send(
            packet.keyId,
            packet.from,
            SENSOR_DATA_RESPONSE_V2,
            responsePayload,
            if (configService.getEnvironmentSensors().validateTimestamp) null else packet.timestampSecondsSince2000
        ) {
            if (!it) {
                logger.info { "Could not send sensor-data response" }
            }
        }

        influxDBClient.recordSensorData(
            updatedSensorData.toInfluxDbRecord(
                sensor.name,
                batteryAdcToMv(
                    packet.from,
                    updatedSensorData.adcBattery
                ),
                packet.from == 10 // no light sensor for #10 - "bak knevegg"
            )
        )

        if (packet.from in outsideSensorIds) {
            // update combined as well
            val outsideData = sensors
                .filter { it.key in outsideSensorIds }
                .filterNot { it.key == packet.from }
                .filter { it.value.lastReceivedMessage is SensorData }
                .map { it.value.lastReceivedMessage as SensorData } + updatedSensorData

            influxDBClient.recordSensorData(
                InfluxDBRecord(
                    Instant.ofEpochMilli(updatedSensorData.receivedAt),
                    "sensor",
                    mapOf(
                        "temperature" to ((outsideData.minOf { it.temperature }).toFloat() / 100).toString(),
                        "humidity" to ((outsideData.maxOf { it.humidity }).toFloat() / 100).toString()
                    ),
                    mapOf(
                        "room" to "outside_combined"
                    )
                )
            )
        }

        return SensorState(
            id = packet.from,
            firmwareVersion = sensorData.firmwareVersion,
            triggerTimeAdjustment = false,
            triggerFirmwareUpdate = false,
            triggerReset = false,
            lastReceivedMessage = updatedSensorData
        )
    }

    private fun setSleepTimeInSeconds(sensorId: Int, sleepTimeInSeconds: Int) {
        configService.setEnvironmentSensor(
            getSensor(sensorId).copy(
                sleepTimeInSeconds = sleepTimeInSeconds
            )
        )
    }
}

data class SensorState(
    val id: Int,
    val firmwareVersion: Int,
    val triggerFirmwareUpdate: Boolean,
    val triggerTimeAdjustment: Boolean,
    val triggerReset: Boolean,
    val lastReceivedMessage: ReceivedMessage? = null
)


data class SensorData(
    val temperature: Long,
    val temperatureError: Boolean,
    val humidity: Long,
    val adcBattery: Long,
    val adcLight: Long,
    val sleepTimeInSeconds: Int,
    val firmwareVersion: Int,
    override val timestampDelta: Long,
    override val receivedAt: Long,
    override val rssi: Int
) : ReceivedMessage() {

    fun toInfluxDbRecord(roomName: String, batteryAdcToMv: Long, skipLight: Boolean) = InfluxDBRecord(
        Instant.ofEpochMilli(receivedAt),
        "sensor",
        toInfluxDbFields(batteryAdcToMv, skipLight),
        mapOf(
            "room" to roomName
        )
    )

    fun toInfluxDbFields(batteryAdcToMv: Long, skipLight: Boolean) = mapOf(
        "temperature" to (temperature.toFloat() / 100).toString(),
        "temperatureError" to (if (temperatureError) 1 else 0).toString(),
        "humidity" to (humidity.toFloat() / 100).toString(),
        "light" to if (skipLight) "0" else adcLight.toString(),
        "battery_volt" to (batteryAdcToMv.toFloat() / 1000).toString(),
        "sleeptime_seconds" to sleepTimeInSeconds.toString(),
        "firmware_version" to firmwareVersion.toString(),
        "clock_delta_seconds" to timestampDelta.toString(),
        "rssi" to rssi.toString(),
    )
}

data class FirmwareInfoRequest(
    override val timestampDelta: Long,
    override val receivedAt: Long,
    override val rssi: Int
) : ReceivedMessage()

data class FirmwareDataRequest(
    val offset: Int,
    val length: Int,
    val maxBytesPerResponse: Int,
    override val timestampDelta: Long,
    override val receivedAt: Long,
    override val rssi: Int
) : ReceivedMessage()

data class FirmwareHolder(
    val filename: String,
    val data: ByteArray
)