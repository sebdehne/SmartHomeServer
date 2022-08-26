package com.dehnes.smarthome.han

import com.dehnes.smarthome.utils.merge
import com.dehnes.smarthome.utils.toUnsignedInt
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.net.Socket


val headerLength = 0 +
        2 + // frame format field - including the frame length
        1 + // dst addr
        1 + // src addr
        1 + // control
        2   // HCS

data class DlmsMessageHeader(
    val payloadLength: Int,
    val segmentation: Int,
    val dstAddr: Int,
    val srcAddr: Int,
    val controll: Int,
    val hcs: Int,
    val rawHeader: ByteArray,
)

data class DlmsMessage(
    val header: DlmsMessageHeader,
    val payload: ByteArray,
    val fcs: Int,
)


fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

val start = "7Ea0e22b211323".decodeHex()

fun ByteArray.toHex(max: Int = -1): String = if (max > -1) {
    this.toList().subList(0, max)
} else {
    this.toList()
}.joinToString(separator = "") { eachByte -> "%02x ".format(eachByte) }

enum class HanDecoderState {
    detectingStart,
    readingPayload
}

class HanDecoder {

    private val logger = KotlinLogging.logger { }
    private var state: HanDecoderState = HanDecoderState.detectingStart
    private var currentHeader: DlmsMessageHeader? = null

    fun decode(buffer: ByteArray, length: Int, onMessage: (message: DlmsMessage) -> Unit): Int {

        when (state) {
            HanDecoderState.detectingStart -> {
                var startPos: Int = -1
                for(pos in 0 until length) {
                    if (buffer[pos].toUnsignedInt() == 0x7e) {
                        startPos = pos
                        break
                    }
                }
                if (startPos < 0) {
                    return length // skip ahead until we find a start flag
                }
                if ((length - startPos) < headerLength + 1) {
                    return 0 // need more data
                }

                val frameFormat = merge(buffer[startPos + 2].toUnsignedInt(), buffer[startPos + 1].toUnsignedInt())
                val formatType = frameFormat shr 12
                val segmentation = (frameFormat and (0b1 shl 11) shr 11)
                val frameLength = (frameFormat and 0b11111111111)
                val dstAddr = buffer[startPos + 3].toUnsignedInt()
                val srcAddr = buffer[startPos + 4].toUnsignedInt()
                val control = buffer[startPos + 5].toUnsignedInt()
                val hcs = merge(buffer[startPos + 7].toUnsignedInt(), buffer[startPos + 6].toUnsignedInt())
                val calculatedHcs = FCS16.calcFcs16(buffer, startPos + 1, headerLength - 2)

                // validate
                return if (formatType == 0b1010 && calculatedHcs == hcs) {
                    state = HanDecoderState.readingPayload
                    val rawHeader = ByteArray(headerLength)
                    System.arraycopy(
                        buffer,
                        startPos + 1,
                        rawHeader,
                        0,
                        headerLength
                    )
                    currentHeader = DlmsMessageHeader(
                        payloadLength = frameLength - headerLength - 2,
                        segmentation = segmentation,
                        dstAddr = dstAddr,
                        srcAddr = srcAddr,
                        controll = control,
                        hcs = hcs,
                        rawHeader = rawHeader
                    )
                    startPos + 1 + headerLength
                } else {
                    length
                }
            }

            HanDecoderState.readingPayload -> {
                val header = currentHeader!!
                val payloadLength = header.payloadLength
                if (length >= (payloadLength + 2 + 1)) {
                    val payload = ByteArray(payloadLength)
                    System.arraycopy(
                        buffer,
                        0,
                        payload,
                        0,
                        payloadLength
                    )
                    val fcs = merge(buffer[payloadLength + 1].toUnsignedInt(), buffer[payloadLength].toUnsignedInt())
                    val endFlag = buffer[payloadLength + 2].toUnsignedInt()

                    // check
                    val toBeChecked = ByteArray(headerLength + payloadLength)
                    System.arraycopy(
                        header.rawHeader,
                        0,
                        toBeChecked,
                        0,
                        headerLength
                    )
                    System.arraycopy(
                        payload,
                        0,
                        toBeChecked,
                        headerLength,
                        payloadLength
                    )

                    val calculatedFcs = FCS16.calcFcs16(toBeChecked, 0, toBeChecked.size)

                    return if (calculatedFcs == fcs && endFlag == 0x7e) {
                        onMessage(
                            DlmsMessage(
                                header,
                                payload,
                                fcs
                            )
                        )
                        state = HanDecoderState.detectingStart
                        currentHeader = null
                        payloadLength + 2 + 1
                    } else {
                        logger.error { "Could not validate payload and FCS" }
                        state = HanDecoderState.detectingStart
                        currentHeader = null
                        0
                    }
                } else {
                    // need more data
                    return 0
                }
            }
        }
    }

    fun readFromTcp(port: Int, host: String) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 10000)
        s.soTimeout = (1000 * 60)
        s.getInputStream().use { inputStream ->

            logger.info { "Connected" }

            val buffer = ByteArray(1024 * 10)
            var writePos = 0

            while (true) {
                if (writePos + 1 >= buffer.size) error("Buffer full")
                val read = inputStream.read(buffer, writePos, buffer.size - writePos)
                if (read > -1) {
                    writePos += read

                    val consumed = decode(buffer, writePos) {
                        logger.info { "Got new msg=${it}" }
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
    }

}

fun main() {
    HanDecoder().readFromTcp(23000, "192.168.1.1")
}