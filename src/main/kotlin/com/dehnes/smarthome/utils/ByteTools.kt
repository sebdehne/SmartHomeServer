package com.dehnes.smarthome.utils

import java.nio.ByteBuffer

fun merge(low: Int, hi: Int): Int {
    val result = hi shl 8
    return result + low
}

fun Byte.toUnsignedInt(): Int = this.toInt().let {
    if (it < 0)
        it + 256
    else
        it
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

fun Boolean.toInt() = if (this) 1 else 0
