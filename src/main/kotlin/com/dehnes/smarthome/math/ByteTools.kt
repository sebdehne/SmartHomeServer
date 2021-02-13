package com.dehnes.smarthome.math

import java.math.BigDecimal
import java.math.RoundingMode

fun merge(low: Byte, hi: Byte): Int {
    val result = hi.toInt() shl 8
    return result + low.toInt()
}

fun divideBy100(input: Int) = BigDecimal(input).divide(
    BigDecimal.valueOf(100),
    2,
    RoundingMode.HALF_UP
).toFloat().toString()