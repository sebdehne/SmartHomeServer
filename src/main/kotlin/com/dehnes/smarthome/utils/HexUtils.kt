package com.dehnes.smarthome.utils

import java.nio.ByteBuffer


fun decodeHexString(hexString: String): ByteArray {
    require(hexString.length % 2 != 1) { "Invalid hexadecimal String supplied: $hexString" }
    val bytes = ByteArray(hexString.length / 2)
    var i = 0
    while (i < hexString.length) {
        bytes[i / 2] = hexToByte(hexString.substring(i, i + 2))
        i += 2
    }
    return bytes
}

fun hexToByte(hexString: String): Byte {
    val firstDigit: Int = toDigit(hexString[0])
    val secondDigit: Int = toDigit(hexString[1])
    return ((firstDigit shl 4) + secondDigit).toByte()
}

private fun toDigit(hexChar: Char): Int {
    val digit = Character.digit(hexChar, 16)
    require(digit != -1) { "Invalid Hexadecimal Character: $hexChar" }
    return digit
}

fun Long.to32Bit(): ByteArray {
    val bytes = ByteArray(8)
    ByteBuffer.wrap(bytes).putLong(this)
    return bytes.copyOfRange(4, 8)
}

fun Int.to32Bit(): ByteArray {
    val bytes = ByteArray(4)
    ByteBuffer.wrap(bytes).putInt(this)
    return bytes
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun readInt32Bits(buf: ByteArray, offset: Int): Int {
    val byteBuffer = ByteBuffer.allocate(4)
    byteBuffer.put(buf, offset, 4)
    byteBuffer.flip()
    return byteBuffer.getInt(0)
}

fun readLong32Bits(buf: ByteArray, offset: Int): Long {
    val byteBuffer = ByteBuffer.allocate(8)
    byteBuffer.put(byteArrayOf(0, 0, 0, 0), 0, 4)
    byteBuffer.put(buf, offset, 4)
    byteBuffer.flip()
    return byteBuffer.getLong(0)
}