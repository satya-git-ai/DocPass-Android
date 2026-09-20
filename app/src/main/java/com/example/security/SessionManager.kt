package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Manages Vault Session Security, Master Key lifecycle, auto-lock timeouts,
 * and user preferences.
 */
class SessionManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("privault_sec_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SALT = "privault_k_salt"
        private const val KEY_VERIFIER = "privault_k_verifier"
        private const val KEY_ENCRYPTED_VMK = "privault_k_enc_vmk"
        private const val KEY_BIOMETRIC_ENABLED = "privault_pref_biometric"
        private const val KEY_BIOMETRIC_ENCRYPTED_VMK = "privault_k_biometric_vmk"
        private const val KEY_AUTO_LOCK_TIMEOUT = "privault_pref_autolock_ms"
        private const val KEY_THEME_MODE = "privault_pref_theme"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "docpass_biometric_key_wrap"

        // Default auto-lock timeout: 1 minute (60,000 ms)
        const val DEFAULT_AUTO_LOCK_MS = 60_000L
    }

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isMasterPinSet = MutableStateFlow(checkIsMasterPinSet())
    val isMasterPinSet: StateFlow<Boolean> = _isMasterPinSet.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _autoLockTimeoutMs = MutableStateFlow(prefs.getLong(KEY_AUTO_LOCK_TIMEOUT, DEFAULT_AUTO_LOCK_MS))
    val autoLockTimeoutMs: StateFlow<Long> = _autoLockTimeoutMs.asStateFlow()

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Transient in-memory Vault Master Key (VMK) — NEVER persisted in plaintext
    private var activeVaultMasterKey: SecretKey? = null
    private var lastUserActivityTimestamp: Long = System.currentTimeMillis()

    private fun checkIsMasterPinSet(): Boolean {
        return prefs.contains(KEY_SALT) && prefs.contains(KEY_VERIFIER) && prefs.contains(KEY_ENCRYPTED_VMK)
    }

    /**
     * Initializes a new Master Password for first launch.
     * Enforces strict password policy (Uppercase, Lowercase, Number, Special char, min 8 chars, no common passwords).
     */
    @Synchronized
    fun setupMasterPin(pin: String): Boolean {
        val validation = PasswordPolicy.validate(pin)
        if (!validation.isValid) return false
        val pinChars = pin.toCharArray()
        try {
            val salt = CryptoManager.generateRandomBytes(32)
            val verifierHash = CryptoManager.computePinVerificationHash(pinChars, salt)

            // Generate brand new Vault Master Key (VMK)
            val vmk = CryptoManager.generateVaultMasterKey()

            // Derive KEK from PIN / Password
            val kek = CryptoManager.deriveKeyFromPin(pinChars, salt)

            // Encrypt VMK with KEK
            val encryptedVmk = CryptoManager.encryptBytes(vmk.encoded, kek)

            prefs.edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_VERIFIER, verifierHash)
                .putString(KEY_ENCRYPTED_VMK, Base64.encodeToString(encryptedVmk, Base64.NO_WRAP))
                .apply()

            activeVaultMasterKey = vmk
            if (_biometricEnabled.value) {
                saveBiometricWrappedVmk(vmk)
            }
            _isMasterPinSet.value = true
            _isLocked.value = false
            updateUserActivity()
            return true
        } finally {
            Arrays.fill(pinChars, '\u0000')
        }
    }

    /**
     * Authenticates with Master PIN and unlocks the vault.
     */
    @Synchronized
    fun unlockWithPin(pin: String): Boolean {
        if (!checkIsMasterPinSet()) return false
        val pinChars = pin.toCharArray()
        try {
            val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
            val storedVerifier = prefs.getString(KEY_VERIFIER, null) ?: return false
            val encryptedVmkBase64 = prefs.getString(KEY_ENCRYPTED_VMK, null) ?: return false

            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val computedVerifier = CryptoManager.computePinVerificationHash(pinChars, salt)

            if (computedVerifier != storedVerifier) {
                return false
            }

            // Derive KEK
            val kek = CryptoManager.deriveKeyFromPin(pinChars, salt)
            val encryptedVmk = Base64.decode(encryptedVmkBase64, Base64.NO_WRAP)

            // Decrypt VMK
            val decryptedVmkBytes = CryptoManager.decryptBytes(encryptedVmk, kek)
            val vmk = SecretKeySpec(decryptedVmkBytes, "AES")
            activeVaultMasterKey = vmk

            if (_biometricEnabled.value) {
                saveBiometricWrappedVmk(vmk)
            }

            _isLocked.value = false
            updateUserActivity()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            Arrays.fill(pinChars, '\u0000')
        }
    }

    /**
     * Changes Master Password without re-encrypting existing vault data.
     * Decrypts VMK with old password and re-encrypts VMK with new password.
     */
    @Synchronized
    fun changeMasterPin(oldPin: String, newPin: String): Boolean {
        val validation = PasswordPolicy.validate(newPin)
        if (!validation.isValid) return false
        val oldPinChars = oldPin.toCharArray()
        val newPinChars = newPin.toCharArray()
        try {
            val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
            val storedVerifier = prefs.getString(KEY_VERIFIER, null) ?: return false
            val encryptedVmkBase64 = prefs.getString(KEY_ENCRYPTED_VMK, null) ?: return false

            val oldSalt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val computedVerifier = CryptoManager.computePinVerificationHash(oldPinChars, oldSalt)

            if (computedVerifier != storedVerifier) {
                return false
            }

            // Decrypt current VMK
            val oldKek = CryptoManager.deriveKeyFromPin(oldPinChars, oldSalt)
            val encryptedVmk = Base64.decode(encryptedVmkBase64, Base64.NO_WRAP)
            val vmkBytes = CryptoManager.decryptBytes(encryptedVmk, oldKek)
            val currentVmk = SecretKeySpec(vmkBytes, "AES")

            // Create new salt and new KEK
            val newSalt = CryptoManager.generateRandomBytes(32)
            val newVerifier = CryptoManager.computePinVerificationHash(newPinChars, newSalt)
            val newKek = CryptoManager.deriveKeyFromPin(newPinChars, newSalt)

            // Encrypt existing VMK with new KEK
            val newEncryptedVmk = CryptoManager.encryptBytes(currentVmk.encoded, newKek)

            prefs.edit()
                .putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
                .putString(KEY_VERIFIER, newVerifier)
                .putString(KEY_ENCRYPTED_VMK, Base64.encodeToString(newEncryptedVmk, Base64.NO_WRAP))
                .apply()

            activeVaultMasterKey = currentVmk
            if (_biometricEnabled.value) {
                saveBiometricWrappedVmk(currentVmk)
            }
            updateUserActivity()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            Arrays.fill(oldPinChars, '\u0000')
            Arrays.fill(newPinChars, '\u0000')
        }
    }

    private fun getOrCreateBiometricKeyStoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    BIOMETRIC_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            } else {
                val keyEntry = keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                keyEntry?.secretKey
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBiometricWrappedVmk(vmk: SecretKey) {
        try {
            val key = getOrCreateBiometricKeyStoreKey()
            if (key != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(vmk.encoded)
                val combined = ByteArray(iv.size + encryptedBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
                prefs.edit()
                    .putString(KEY_BIOMETRIC_ENCRYPTED_VMK, Base64.encodeToString(combined, Base64.NO_WRAP))
                    .apply()
            } else {
                // Fallback for JVM/test environments without AndroidKeyStore
                val salt = CryptoManager.generateRandomBytes(16)
                val fallbackKey = CryptoManager.deriveKeyFromPin(context.packageName.toCharArray(), salt)
                val enc = CryptoManager.encryptBytes(vmk.encoded, fallbackKey)
                val payload = Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
                prefs.edit()
                    .putString(KEY_BIOMETRIC_ENCRYPTED_VMK, payload)
                    .apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreVmkFromBiometrics(): SecretKey? {
        try {
            val encData = prefs.getString(KEY_BIOMETRIC_ENCRYPTED_VMK, null) ?: return null
            if (encData.contains(":")) {
                // Fallback decode
                val parts = encData.split(":")
                val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                val fallbackKey = CryptoManager.deriveKeyFromPin(context.packageName.toCharArray(), salt)
                val decrypted = CryptoManager.decryptBytes(cipherBytes, fallbackKey)
                return SecretKeySpec(decrypted, "AES")
            }
            val combined = Base64.decode(encData, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)

            val key = getOrCreateBiometricKeyStoreKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(ciphertext)
            return SecretKeySpec(decrypted, "AES")
        } catch (e: Exception) {
            return null
        }
    }

    private fun clearBiometricKey() {
        try {
            prefs.edit().remove(KEY_BIOMETRIC_ENCRYPTED_VMK).apply()
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Used by Biometric Unlock when biometric authentication succeeds.
     * Restores VMK securely across app restarts.
     */
    @Synchronized
    fun unlockWithSavedSession(): Boolean {
        if (activeVaultMasterKey != null) {
            _isLocked.value = false
            updateUserActivity()
            return true
        }
        if (_biometricEnabled.value) {
            val restored = restoreVmkFromBiometrics()
            if (restored != null) {
                activeVaultMasterKey = restored
                _isLocked.value = false
                updateUserActivity()
                return true
            }
        }
        return false
    }

    /**
     * Retrieves the active Vault Master Key for cryptographic operations.
     * Throws SecurityException if the vault is currently locked.
     */
    @Synchronized
    fun getVaultMasterKey(): SecretKey {
        val key = activeVaultMasterKey
        if (key == null || _isLocked.value) {
            throw SecurityException("Vault is locked. Cannot access master encryption key.")
        }
        return key
    }

    /**
     * Locks the vault immediately and wipes in-memory master key.
     */
    @Synchronized
    fun lockVault() {
        _isLocked.value = true
        activeVaultMasterKey = null
    }

    /**
     * Updates activity timestamp. Call on UI interactions.
     */
    fun updateUserActivity() {
        lastUserActivityTimestamp = System.currentTimeMillis()
    }

    /**
     * Checks if inactivity timeout has elapsed and locks if needed.
     */
    fun checkAutoLockOnInactivity() {
        if (_isLocked.value) return
        val timeout = _autoLockTimeoutMs.value
        if (timeout <= 0) {
            // Immediate lock on backgrounding
            lockVault()
            return
        }
        val elapsed = System.currentTimeMillis() - lastUserActivityTimestamp
        if (elapsed >= timeout) {
            lockVault()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _biometricEnabled.value = enabled
        if (enabled) {
            activeVaultMasterKey?.let { saveBiometricWrappedVmk(it) }
        } else {
            clearBiometricKey()
        }
    }

    fun setAutoLockTimeout(timeoutMs: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_TIMEOUT, timeoutMs).apply()
        _autoLockTimeoutMs.value = timeoutMs
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    /**
     * Factory reset: Wipes all keys and authentication settings.
     */
    @Synchronized
    fun wipeSecurityData() {
        clearBiometricKey()
        prefs.edit().clear().apply()
        activeVaultMasterKey = null
        _isMasterPinSet.value = false
        _isLocked.value = true
    }
}
