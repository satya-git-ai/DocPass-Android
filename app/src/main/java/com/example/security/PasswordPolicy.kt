package com.example.security

/**
 * Strict Password Policy Validator for Privault / Doc Vault.
 *
 * Rules:
 * 1. Must contain at least 1 uppercase letter (A–Z)
 * 2. Must contain at least 1 lowercase letter (a–z)
 * 3. Must contain at least 1 number (0–9)
 * 4. Must contain at least 1 special character from: ! @ # $ % ^ & *
 * 5. Minimum 8 characters in length
 * 6. Reject passwords containing common or easily guessable passwords, including:
 *    - Password123!
 *    - Admin@123
 *    - Welcome123!
 *    - Qwerty123!
 *    - Obvious variations of common passwords (e.g. leetspeak substitutions, root word combinations).
 */
object PasswordPolicy {

    val ALLOWED_SPECIAL_CHARS = setOf('!', '@', '#', '$', '%', '^', '&', '*')

    // Common root words to reject even if modified with 123 or special symbols
    private val COMMON_ROOT_WORDS = listOf(
        "password",
        "pass",
        "admin",
        "administrator",
        "welcome",
        "qwerty",
        "letmein",
        "iloveyou",
        "dragon",
        "monkey",
        "master",
        "docpass",
        "privault",
        "docvault",
        "vault",
        "default",
        "secret",
        "login",
        "access",
        "system",
        "user",
        "security"
    )

    private val EXACT_COMMON_PASSWORDS = setOf(
        "password123!",
        "admin@123",
        "welcome123!",
        "qwerty123!",
        "password@123",
        "admin123!",
        "welcome@123",
        "qwerty@123",
        "pass123!",
        "p@ssword123!",
        "p@ssw0rd123!",
        "admin!123",
        "root@123",
        "master@123",
        "master123!"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val hasMinLength: Boolean,
        val hasUppercase: Boolean,
        val hasLowercase: Boolean,
        val hasDigit: Boolean,
        val hasSpecialChar: Boolean,
        val notCommon: Boolean,
        val detectedCommonPattern: String? = null
    )

    fun validate(password: String): ValidationResult {
        val errors = mutableListOf<String>()

        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it in 'A'..'Z' }
        val hasLowercase = password.any { it in 'a'..'z' }
        val hasDigit = password.any { it in '0'..'9' }
        val hasSpecialChar = password.any { it in ALLOWED_SPECIAL_CHARS }

        if (!hasMinLength) {
            errors.add("Password must be at least 8 characters long")
        }
        if (!hasUppercase) {
            errors.add("Must contain at least 1 uppercase letter (A–Z)")
        }
        if (!hasLowercase) {
            errors.add("Must contain at least 1 lowercase letter (a–z)")
        }
        if (!hasDigit) {
            errors.add("Must contain at least 1 number (0–9)")
        }
        if (!hasSpecialChar) {
            errors.add("Must contain at least 1 special character from: ! @ # $ % ^ & *")
        }

        // Check for common or easily guessable passwords & variations
        val lower = password.lowercase().trim()
        var notCommon = true
        var detectedPattern: String? = null

        if (EXACT_COMMON_PASSWORDS.contains(lower)) {
            notCommon = false
            detectedPattern = password
            errors.add("Password is too common and easily guessable (e.g. \"$password\")")
        } else {
            // Leetspeak normalization: replace '@'->'a', '$'->'s', '0'->'o', '1'->'i', '3'->'e', '4'->'a', '5'->'s', '7'->'t', '!'->'i'
            val normalized = lower
                .replace('@', 'a')
                .replace('$', 's')
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't')
                .replace('!', 'i')
                .filter { it.isLetter() }

            for (root in COMMON_ROOT_WORDS) {
                if (normalized.contains(root)) {
                    notCommon = false
                    detectedPattern = root
                    errors.add("Password contains common or easily guessable word: \"$root\"")
                    break
                }
            }
        }

        val isValid = hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecialChar && notCommon

        return ValidationResult(
            isValid = isValid,
            errors = errors,
            hasMinLength = hasMinLength,
            hasUppercase = hasUppercase,
            hasLowercase = hasLowercase,
            hasDigit = hasDigit,
            hasSpecialChar = hasSpecialChar,
            notCommon = notCommon,
            detectedCommonPattern = detectedPattern
        )
    }
}
