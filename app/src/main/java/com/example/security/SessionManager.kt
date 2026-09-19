package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Arrays
import javax.crypto.SecretKey
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
        private const val KEY_AUTO_LOCK_TIMEOUT = "privault_pref_autolock_ms"
        private const val KEY_THEME_MODE = "privault_pref_theme"

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
            activeVaultMasterKey = SecretKeySpec(decryptedVmkBytes, "AES")

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
            updateUserActivity()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            Arrays.fill(oldPinChars, '\u0000')
            Arrays.fill(newPinChars, '\u0000')
        }
    }

    /**
     * Used by Biometric Unlock when biometric authentication succeeds.
     */
    @Synchronized
    fun unlockWithSavedSession(): Boolean {
        if (activeVaultMasterKey != null) {
            _isLocked.value = false
            updateUserActivity()
            return true
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
        // Keep VMK in memory for biometric unlock if biometric is enabled and session hasn't expired,
        // or clear completely if full lock
        if (!_biometricEnabled.value) {
            activeVaultMasterKey = null
        }
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
        prefs.edit().clear().apply()
        activeVaultMasterKey = null
        _isMasterPinSet.value = false
        _isLocked.value = true
    }
}
