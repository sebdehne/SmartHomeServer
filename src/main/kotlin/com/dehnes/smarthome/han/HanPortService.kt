package com.dehnes.smarthome.han

import com.dehnes.smarthome.datalogging.InfluxDBClient
import com.dehnes.smarthome.energy_pricing.EnergyPriceService
import com.dehnes.smarthome.utils.PersistenceService
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService

data class HanData(
    val totalPowerImport: Long,
    val totalPowerExport: Long,
    val totalReactivePowerImport: Long,
    val totalReactivePowerExport: Long,
    val currentL1: Long,
    val currentL2: Long,
    val currentL3: Long,
    val voltageL1: Long,
    val voltageL2: Long,
    val voltageL3: Long,
    val totalEnergyImport: Long?,
    val totalEnergyExport: Long?,
    val totalReactiveEnergyImport: Long?,
    val totalReactiveEnergyExport: Long?,

    val createdAt: Instant = Instant.now()
)

class HanPortService(
    private val host: String,
    private val port: Int,
    private val executorService: ExecutorService,
    influxDBClient: InfluxDBClient,
    energyPriceService: EnergyPriceService,
    private val persistenceService: PersistenceService,
) {

    private val hanDataService = HanDataService(
        influxDBClient,
        energyPriceService
    )

    private val logger = KotlinLogging.logger { }
    val listeners = CopyOnWriteArrayList<(HanData) -> Unit>()

    @Volatile
    private var isStarted = false

    @Volatile
    private var hanPortConnection: HanPortConnection? = null

    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        Thread {
            while (isStarted) {
                try {
                    readLoop()
                } catch (e: Exception) {
                    kotlin.runCatching { hanPortConnection?.close() }
                    hanPortConnection = null
                    logger.error("", e)
                }
                Thread.sleep(10 * 1000)
            }
        }.start()
    }

    fun stop() {
        if (!isStarted) {
            return
        }
        isStarted = false
    }

    private fun readLoop() {
        val buffer = ByteArray(1024 * 10)
        var writePos = 0
        val hanDecoder = HanDecoder(persistenceService)

        while (true) {
            if (hanPortConnection == null) {
                hanPortConnection = HanPortConnection.open(host, port).also {
                    logger.info { "Connected" }
                    writePos = 0
                }
            }


            if (hanPortConnection != null) {
                if (writePos + 1 >= buffer.size) error("Buffer full")
                val read = hanPortConnection!!.read(buffer, writePos, buffer.size - writePos)
                check(read > -1) { "EOF while reading from HAN-port" }
                writePos += read

                val consumed = hanDecoder.decode(buffer, writePos) { hdlcFrame ->
                    val dlmsMessage = DLMSDecoder.decode(hdlcFrame)
                    val hanData = mapToHanData(dlmsMessage)
                    logger.info { "Got new msg=${hdlcFrame} hanData=$hanData" }
                    executorService.submit {
                        val listeners = listOf(hanDataService::onNewData) + this.listeners
                        listeners.forEach { l ->
                            try {
                                l(hanData)
                            } catch (e: Exception) {
                                logger.error(e) { "Error from hanListener" }
                            }
                        }
                    }
                }

                if (consumed > 0) {
                    // wrap the buffer
                    System.arraycopy(
                        buffer,
                        consumed,
                        buffer,
                        0,
                        writePos - consumed
                    )
                    writePos -= consumed
                }
            }
        }
    }

    // https://www.nek.no/wp-content/uploads/2018/10/Kamstrup-HAN-NVE-interface-description_rev_3_1.pdf
    private fun mapToHanData(dlmsMessage: DLMSMessage) = HanData(
        totalPowerImport = (dlmsMessage.findElement("1.1.1.7.0.255") as NumberElement).value,
        totalPowerExport = (dlmsMessage.findElement("1.1.2.7.0.255") as NumberElement).value,
        totalReactivePowerImport = (dlmsMessage.findElement("1.1.3.7.0.255") as NumberElement).value,
        totalReactivePowerExport = (dlmsMessage.findElement("1.1.4.7.0.255") as NumberElement).value,
        currentL1 = (dlmsMessage.findElement("1.1.31.7.0.255") as NumberElement).value * 10, // in milliAmpere
        currentL2 = (dlmsMessage.findElement("1.1.51.7.0.255") as NumberElement).value * 10, // in milliAmpere
        currentL3 = (dlmsMessage.findElement("1.1.71.7.0.255") as NumberElement).value * 10, // in milliAmpere
        voltageL1 = (dlmsMessage.findElement("1.1.32.7.0.255") as NumberElement).value,
        voltageL2 = (dlmsMessage.findElement("1.1.52.7.0.255") as NumberElement).value,
        voltageL3 = (dlmsMessage.findElement("1.1.72.7.0.255") as NumberElement).value,
        totalEnergyImport = (dlmsMessage.findElement("1.1.1.8.0.255") as NumberElement?)?.value?.let { it * 10 }, // Wh
        totalEnergyExport = (dlmsMessage.findElement("1.1.2.8.0.255") as NumberElement?)?.value?.let { it * 10 }, // Wh
        totalReactiveEnergyImport = (dlmsMessage.findElement("1.1.3.8.0.255") as NumberElement?)?.value?.let { it * 10 }, // Wh
        totalReactiveEnergyExport = (dlmsMessage.findElement("1.1.4.8.0.255") as NumberElement?)?.value?.let { it * 10 }, // Wh
    )
}