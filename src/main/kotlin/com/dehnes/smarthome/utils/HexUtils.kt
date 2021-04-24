package com.dehnes.smarthome.utils


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

fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }

private fun toDigit(hexChar: Char): Int {
    val digit = Character.digit(hexChar, 16)
    require(digit != -1) { "Invalid Hexadecimal Character: $hexChar" }
    return digit
}
