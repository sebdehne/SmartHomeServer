package com.dehnes.smarthome.han

import com.dehnes.smarthome.utils.DateTimeUtils.zoneId
import com.dehnes.smarthome.utils.merge
import com.dehnes.smarthome.utils.readLong32Bits
import com.dehnes.smarthome.utils.toUnsignedInt
import java.time.Instant
import java.time.LocalDateTime

data class DLMSMessage(
    val timestamp: Instant,
    val elements: List<DLMSElement>
) {
    fun findElement(id: String): DLMSElement? =
        elements.indexOfFirst { it is OctetStringString && it.str == id }.let { index ->
            if (index > -1) {
                elements[index + 1]
            } else null
        }
}

sealed class DLMSElement
data class VisibleString(
    val str: String
) : DLMSElement()

data class OctetStringString(
    val str: String
) : DLMSElement()

data class NumberElement(
    val value: Long
) : DLMSElement()


object DLMSDecoder {

    fun decode(hdlcFrame: HDLCFrame): DLMSMessage {

        val payload = hdlcFrame.payload
        val length = payload[8].toUnsignedInt()
        check(length == 12)
        val year = merge(payload[10].toUnsignedInt(), payload[9].toUnsignedInt())
        val month = payload[11].toUnsignedInt()
        val date = payload[12].toUnsignedInt()
        // 13 - day of week 13
        val hour = payload[14].toUnsignedInt()
        val minute = payload[15].toUnsignedInt()
        val second = payload[16].toUnsignedInt()
        // 17 Hundredths of second
        val uTCOffset = merge(payload[19].toUnsignedInt(), payload[18].toUnsignedInt())
        val clockStatus = payload[20].toUnsignedInt()

        val struct = payload[21].toUnsignedInt()
        check(struct == 2)
        val numberOfElements = payload[22].toUnsignedInt()

        var elements = mutableListOf<DLMSElement>()

        var pos = 23
        repeat(numberOfElements) {
            val type = payload[pos++].toUnsignedInt()
            elements.add(
                when (type) {
                    0x0a -> {
                        val elementLength = payload[pos++].toUnsignedInt()
                        val data = ByteArray(elementLength)
                        System.arraycopy(
                            payload,
                            pos,
                            data,
                            0,
                            elementLength
                        )
                        pos += elementLength
                        VisibleString(String(data))
                    }

                    0x09 -> {
                        val elementLength = payload[pos++].toUnsignedInt()
                        val data = ByteArray(elementLength)
                        System.arraycopy(
                            payload,
                            pos,
                            data,
                            0,
                            elementLength
                        )
                        pos += elementLength
                        OctetStringString(data.joinToString(separator = ".") { it.toUnsignedInt().toString() })
                    }

                    0x06 -> {
                        NumberElement(readLong32Bits(payload, pos)).apply {
                            pos += 4
                        }
                    }

                    0x12 -> NumberElement(
                        merge(
                            payload[pos + 1].toUnsignedInt(),
                            payload[pos].toUnsignedInt()
                        ).toLong()
                    ).also {
                        pos += 2
                    }

                    else -> error("Unknown element type=$type")
                }
            )
        }

        return DLMSMessage(
            LocalDateTime.of(
                year, month, date,
                hour,
                minute,
                second
            ).atZone(zoneId).toInstant(),
            elements
        )

    }

}