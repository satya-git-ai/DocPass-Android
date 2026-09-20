package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.CryptoManager
import com.example.security.PasswordGenerator
import com.example.security.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DocPass", appName)
    }

    @Test
    fun `crypto manager encrypt and decrypt string`() {
        val masterKey = CryptoManager.generateVaultMasterKey()
        val plainText = "SecretPassword123!@#"

        val encrypted = CryptoManager.encryptString(plainText, masterKey)
        assertNotEquals(plainText, encrypted)

        val decrypted = CryptoManager.decryptString(encrypted, masterKey)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `crypto manager encrypt and decrypt bytes`() {
        val masterKey = CryptoManager.generateVaultMasterKey()
        val rawBytes = "Sample confidential document binary data".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoManager.encryptBytes(rawBytes, masterKey)
        val decrypted = CryptoManager.decryptBytes(encrypted, masterKey)

        assertEquals(String(rawBytes), String(decrypted))
    }

    @Test(expected = AEADBadTagException::class)
    fun `crypto manager fails on incorrect key`() {
        val masterKey1 = CryptoManager.generateVaultMasterKey()
        val masterKey2 = CryptoManager.generateVaultMasterKey()
        val plainText = "Confidential Vault Data"

        val encrypted = CryptoManager.encryptString(plainText, masterKey1)
        CryptoManager.decryptString(encrypted, masterKey2)
    }

    @Test
    fun `password generator creates strong entropy passwords`() {
        val pwd16 = PasswordGenerator.generate(16)
        assertEquals(16, pwd16.length)

        val pwd24 = PasswordGenerator.generate(24)
        assertEquals(24, pwd24.length)

        val strength = PasswordGenerator.estimateStrength(pwd24)
        assertTrue(strength == PasswordGenerator.Strength.STRONG || strength == PasswordGenerator.Strength.VERY_STRONG)
    }

    @Test
    fun `session manager master pin lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = SessionManager(context)
        sessionManager.wipeSecurityData()

        assertFalse(sessionManager.isMasterPinSet.value)

        // Setup compliant Password
        val testPassword = "Xk9#mP2\$vL8*qR"
        val setupSuccess = sessionManager.setupMasterPin(testPassword)
        assertTrue(setupSuccess)
        assertTrue(sessionManager.isMasterPinSet.value)
        assertFalse(sessionManager.isLocked.value)

        // Lock
        sessionManager.lockVault()
        assertTrue(sessionManager.isLocked.value)

        // Incorrect PIN
        val wrongUnlock = sessionManager.unlockWithPin("WrongPassword123!")
        assertFalse(wrongUnlock)
        assertTrue(sessionManager.isLocked.value)

        // Correct PIN
        val correctUnlock = sessionManager.unlockWithPin(testPassword)
        assertTrue(correctUnlock)
        assertFalse(sessionManager.isLocked.value)
    }
}
