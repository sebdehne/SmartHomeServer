package com.dehnes.smarthome.han

import com.dehnes.smarthome.utils.merge
import com.dehnes.smarthome.utils.toUnsignedInt
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*


val headerLength = 0 +
        2 + // frame format field - including the frame length
        1 + // dst addr
        1 + // src addr
        1 + // control
        2   // HCS

data class HDLCFrameHeader(
    val payloadLength: Int,
    val segmentation: Int,
    val dstAddr: Int,
    val srcAddr: Int,
    val control: Int,
    val frameType: HDLCFrameType,
    val hcs: Int,
    val rawHeader: ByteArray,
)

enum class HDLCFrameType {
    information,
    supervisory,
    unnumbered;

    companion object {
        fun fromControl(control: Int) = when {
            control and 0b11 == 0b00 -> information
            control and 0b11 == 0b10 -> supervisory
            control and 0b11 == 0b11 -> unnumbered
            else -> error("Unknown frame type. control=$control")
        }
    }
}

data class HDLCFrame(
    val header: HDLCFrameHeader,
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

    private val random = Random()
    private val logger = KotlinLogging.logger { }
    private var state: HanDecoderState = HanDecoderState.detectingStart
    private var currentHeader: HDLCFrameHeader? = null

    fun decode(buffer: ByteArray, length: Int, onMessage: (message: HDLCFrame) -> Unit): Int {

        when (state) {
            HanDecoderState.detectingStart -> {
                var startPos: Int = -1
                for (pos in 0 until length) {
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
                    currentHeader = HDLCFrameHeader(
                        payloadLength = frameLength - headerLength - 2,
                        segmentation = segmentation,
                        dstAddr = dstAddr,
                        srcAddr = srcAddr,
                        control = control,
                        frameType = HDLCFrameType.fromControl(control),
                        hcs = hcs,
                        rawHeader = rawHeader
                    )
                    startPos + 1 + headerLength
                } else {
                    val skip = random.nextInt(length)
                    // experience shows that when stty settings are wrong, the data looks somewhat good, but is missing some bytes
                    logger.warn { "Invalida data received - possible incorrect stty settings, skipping $skip" }
                    skip
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
                            HDLCFrame(
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

}
