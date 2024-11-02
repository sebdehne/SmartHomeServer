package com.dehnes.smarthome.utils

import java.nio.ByteBuffer

fun merge(low: Int, hi: Int): Int {
    val result = hi shl 8
    return result + low
}

fun ByteArray.write(src: ByteArray, dstOffset: Int) {
    System.arraycopy(src, 0, this, dstOffset, src.size)
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

fun Long.to16Bit(): ByteArray {
    val bytes = ByteArray(8)
    ByteBuffer.wrap(bytes).putLong(this)
    return bytes.copyOfRange(6, 8)
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

fun readInt16Bits(buf: ByteArray, offset: Int): Int {
    val byteBuffer = ByteBuffer.allocate(4)
    byteBuffer.put(0)
    byteBuffer.put(0)
    byteBuffer.put(buf, offset, 2)
    byteBuffer.flip()
    return byteBuffer.getInt(0)
}

fun readLong32Bits(buf: ByteArray, offset: Int): Long {
    val byteBuffer = ByteBuffer.allocate(8)
    byteBuffer.put(buf, offset, 4)
    byteBuffer.flip()
    return byteBuffer.getInt(0).toLong()
}

fun Boolean.toInt() = if (this) 1 else 0
