package com.dehnes.smarthome.math

fun merge(low: Int, hi: Int): Int {
    val result = hi shl 8
    return result + low
}
