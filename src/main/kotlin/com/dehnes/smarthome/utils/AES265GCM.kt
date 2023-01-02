package com.dehnes.smarthome.utils

import com.dehnes.smarthome.config.ConfigService
import com.dehnes.smarthome.objectMapper
import mu.KotlinLogging
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random


class AES265GCM(
    configService: ConfigService
) {

    companion object {
        const val IV_SIZE = 12
        const val TAGSIZE = 16

        fun overhead() = IV_SIZE + TAGSIZE
    }

    private val keys = configService.getAesKeys().map { (keyId, keyString) ->
        keyId.toInt() to keyString.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }.toMap()

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
        decodeHexString("DDC393D552DA8699292E8340208EEFE5CCE97B53F77B0AA91E83E6B79C0C7540C15FBB28A2FAB1FE8996579F796CD2A658069D36519B19E55A3D5AF4E5")

    val aeS265GCM = AES265GCM(ConfigService(objectMapper()))
    val (keyId, plainText) = aeS265GCM.decrypt(decodeHexString) ?: error("Could not decrypt")
    println("Done with keyId=$keyId: ${plainText.toString(Charset.defaultCharset())}")

    val ar = byteArrayOf(-1, -1, -1, -48)
    val ar2 = byteArrayOf(0, 0, 33, -73)
    val result = readLong32Bits(ar, 0).toFloat() / 100
    println(ar)
}