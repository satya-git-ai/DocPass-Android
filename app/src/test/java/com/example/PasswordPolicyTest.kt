package com.example

import com.example.security.PasswordPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun testCommonPasswordsRejected() {
        // Required examples from prompt
        val rejectedPasswords = listOf(
            "Password123!",
            "Admin@123",
            "Welcome123!",
            "Qwerty123!",
            "Password@123",
            "admin123!",
            "p@ssw0rd123!",
            "P@ssword123!",
            "Admin!123"
        )

        for (pwd in rejectedPasswords) {
            val result = PasswordPolicy.validate(pwd)
            assertFalse("Expected '$pwd' to be rejected by strict policy", result.isValid)
        }
    }

    @Test
    fun testComplexityRequirements() {
        // Missing uppercase
        assertFalse(PasswordPolicy.validate("secure123!").hasUppercase)
        assertFalse(PasswordPolicy.validate("secure123!").isValid)

        // Missing lowercase
        assertFalse(PasswordPolicy.validate("SECURE123!").hasLowercase)
        assertFalse(PasswordPolicy.validate("SECURE123!").isValid)

        // Missing number
        assertFalse(PasswordPolicy.validate("SecureTest!").hasDigit)
        assertFalse(PasswordPolicy.validate("SecureTest!").isValid)

        // Missing special character from !@#$%^&*
        assertFalse(PasswordPolicy.validate("Secure12345").hasSpecialChar)
        assertFalse(PasswordPolicy.validate("Secure12345").isValid)

        // Too short (< 8 chars)
        assertFalse(PasswordPolicy.validate("S1!a").hasMinLength)
        assertFalse(PasswordPolicy.validate("S1!a").isValid)
    }

    @Test
    fun testValidStrongPasswordAccepted() {
        val validPasswords = listOf(
            "Xk9#mP2\$vL8*qR",
            "J7&hY2!zK9@mW1",
            "T#8vK9@L2*pM5!",
            "Blue9#Dolphin*"
        )

        for (pwd in validPasswords) {
            val result = PasswordPolicy.validate(pwd)
            assertTrue("Expected '$pwd' to be valid", result.isValid)
        }
    }
}
