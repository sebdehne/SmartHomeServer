package com.dehnes.smarthome.math

import java.math.BigDecimal
import java.math.RoundingMode

fun merge(low: Int, hi: Int): Int {
    val result = hi shl 8
    return result + low
}

fun divideBy100(input: Int) = BigDecimal(input).divide(
    BigDecimal.valueOf(100),
    2,
    RoundingMode.HALF_UP
).toFloat().toString()