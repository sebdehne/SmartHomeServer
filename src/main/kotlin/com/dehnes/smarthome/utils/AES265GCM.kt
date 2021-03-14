package com.dehnes.smarthome.utils

import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


object AES265GCM {

    fun encrypt(): ByteArray {
        // Get Cipher Instance
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        // Create SecretKeySpec
        val keySpec = SecretKeySpec(
            intArrayOf(
                0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65, 0x73, 0x1c, 0x6d, 0x6a,
                0x8f, 0x94, 0x67, 0x30, 0x83, 0x08, 0xfe, 0xff, 0xe9, 0x92,
                0x86, 0x65, 0x73, 0x1c, 0x6d, 0x6a, 0x8f, 0x94, 0x67, 0x30,
                0x83, 0x08
            ).map { it.toByte() }.toByteArray(), "AES"
        )
        // Create GCMParameterSpec
        val gcmParameterSpec = GCMParameterSpec(16 * 8, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        // Initialize Cipher for ENCRYPT_MODE
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec)

        // Perform Encryption

        val c: Char = '\u0000'
        val bytes = cipher.doFinal(("Hello$c").toByteArray())
        println(bytes.map { it.toUByte() })

        return bytes
    }


    fun decrypt(cipherText: ByteArray?) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val keySpec = SecretKeySpec(
            intArrayOf(
                0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65, 0x73, 0x1c, 0x6d, 0x6a,
                0x8f, 0x94, 0x67, 0x30, 0x83, 0x08, 0xfe, 0xff, 0xe9, 0x92,
                0x86, 0x65, 0x73, 0x1c, 0x6d, 0x6a, 0x8f, 0x94, 0x67, 0x30,
                0x83, 0x08
            ).map { it.toByte() }.toByteArray(), "AES"
        )

        val gcmParameterSpec = GCMParameterSpec(16 * 8, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
        val decryptedText = cipher.doFinal(cipherText)

        println(decryptedText.toString(Charset.defaultCharset()))
    }


}

fun main() {
    AES265GCM.decrypt(
        AES265GCM.encrypt()
    )
}