package com.example.security

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-grade Cryptographic Engine for Privault.
 *
 * Implements:
 * - AES-256-GCM (Authenticated Encryption with Associated Data - AEAD)
 * - 128-bit authentication tags for integrity & tamper resistance
 * - 12-byte cryptographically secure random IVs per encryption
 * - PBKDF2WithHmacSHA256 with 100,000 iterations for master key derivation
 * - 256-bit Vault Master Key (VMK) wrapped with Master PIN Key-Encryption-Key (KEK)
 */
object CryptoManager {
    private const val AES_KEY_SIZE_BITS = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_SALT_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure random byte array.
     */
    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Generates a new random 256-bit AES Vault Master Key (VMK).
     */
    fun generateVaultMasterKey(): SecretKey {
        val keyBytes = generateRandomBytes(AES_KEY_SIZE_BITS / 8)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Derives a 256-bit Key Encryption Key (KEK) or verifier from a user PIN/password and salt using PBKDF2WithHmacSHA256.
     */
    fun deriveKeyFromPin(pin: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, AES_KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Generates a cryptographic verification hash of the PIN for quick authentication checks.
     */
    fun computePinVerificationHash(pin: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns a formatted Base64 string containing: [12-byte IV] + [Ciphertext + 16-byte Tag]
     */
    fun encryptString(plainText: String, secretKey: SecretKey): String {
        val iv = generateRandomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts an encrypted string previously encrypted with encryptString.
     */
    fun decryptString(encryptedBase64: String, secretKey: SecretKey): String {
        if (encryptedBase64.isEmpty()) return ""
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Corrupted encrypted payload")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decrypted = cipher.doFinal(cipherBytes)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Encrypts raw byte array using AES-256-GCM.
     */
    fun encryptBytes(data: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = generateRandomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(data)
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return combined
    }

    /**
     * Decrypts raw byte array using AES-256-GCM.
     */
    fun decryptBytes(encryptedCombined: ByteArray, secretKey: SecretKey): ByteArray {
        if (encryptedCombined.size < GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Corrupted encrypted payload")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedCombined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherBytes = ByteArray(encryptedCombined.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedCombined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherBytes)
    }

    /**
     * Encrypts input stream to output stream using AES-256-GCM.
     * Writes 12-byte IV at start of stream, followed by ciphertext and authentication tag.
     */
    fun encryptStream(input: InputStream, output: OutputStream, secretKey: SecretKey) {
        val iv = generateRandomBytes(GCM_IV_LENGTH_BYTES)
        output.write(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut)
            cipherOut.flush()
        }
    }

    /**
     * Decrypts encrypted stream from input stream to output stream.
     */
    fun decryptStream(input: InputStream, output: OutputStream, secretKey: SecretKey) {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val bytesRead = input.read(iv)
        if (bytesRead != GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted stream: missing IV")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        CipherInputStream(input, cipher).use { cipherIn ->
            cipherIn.copyTo(output)
            output.flush()
        }
    }
}
