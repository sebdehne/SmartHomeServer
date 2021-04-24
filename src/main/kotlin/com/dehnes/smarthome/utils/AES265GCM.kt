package com.dehnes.smarthome.utils

import mu.KotlinLogging
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random


class AES265GCM(
    persistenceService: PersistenceService
) {

    companion object {
        const val IV_SIZE = 12
        const val TAGSIZE = 16

        fun overhead() = IV_SIZE + TAGSIZE
    }

    private val keys = persistenceService["AES265GCM.keys", "key is missing"]!!
        .split(",")
        .associate { chunk ->
            val split = chunk.split(":")
            split[0].toInt() to split[1].chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    private val logger = KotlinLogging.logger { }

    /**
     * Encrypt the data using GCM/AES-256. The result is the cipertext plus 28 bytes for the tag (16) and the IV (12)
     */
    fun encrypt(data: ByteArray, keyId: Int): ByteArray {

        val iv = ByteArray(IV_SIZE)
        Random.nextBytes(iv)

        // Get Cipher Instance
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        // Create SecretKeySpec
        val keySpec = SecretKeySpec(keys[keyId] ?: error("No such key with id $keyId"), "AES")
        // Create GCMParameterSpec
        val gcmParameterSpec = GCMParameterSpec(16 * 8, iv)

        // Initialize Cipher for ENCRYPT_MODE
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec)

        // Perform Encryption
        val cipherText = cipher.doFinal(data)

        // append IV to end of cipherText
        val cipherTextWithIv = ByteArray(cipherText.size + iv.size)
        System.arraycopy(cipherText, 0, cipherTextWithIv, 0, cipherText.size)
        System.arraycopy(iv, 0, cipherTextWithIv, cipherText.size, iv.size)
        return cipherTextWithIv
    }

    fun decrypt(cipherTextWithIv: ByteArray): Pair<Int, ByteArray>? {
        // split cipherTextWithIv into cipherText and IV
        val iv = ByteArray(IV_SIZE)
        val cipherText = ByteArray(cipherTextWithIv.size - iv.size)
        System.arraycopy(cipherTextWithIv, 0, cipherText, 0, cipherText.size)
        System.arraycopy(cipherTextWithIv, cipherText.size, iv, 0, iv.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        keys.forEach { (keyId, key) ->
            try {
                val keySpec = SecretKeySpec(key, "AES")
                val gcmParameterSpec = GCMParameterSpec(16 * 8, iv)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
                val decryptedText = cipher.doFinal(cipherText)
                return keyId to decryptedText
            } catch (e: Exception) {
                logger.info { "KeyId=$keyId did not work: ${e.localizedMessage}" }
            }
        }

        return null
    }
}

fun main() {

    val decodeHexString =
        decodeHexString("E76CE8FDCF40A2C23A0F218932A7ABAD0B19D4276CC7F8A22FE668CC92993E175E157A314DC72D692F185D0C1F0F5A1B")

    val aeS265GCM = AES265GCM(PersistenceService())
    val (keyId, plainText) = aeS265GCM.decrypt(decodeHexString) ?: error("Could not decrypt")
    println("Done with keyId=$keyId: ${plainText.toString(Charset.defaultCharset())}")
}