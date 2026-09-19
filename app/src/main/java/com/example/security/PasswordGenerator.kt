package com.example.security

import java.security.SecureRandom

/**
 * Local Cryptographic Password Generator and Security Evaluator.
 * 100% offline, cryptographically random, no network calls.
 */
object PasswordGenerator {
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private const val AMBIGUOUS = "il1Lo0O"

    // Curated friendly words for memorable password generation like "Apple@33"
    private val MEMORABLE_WORDS = listOf(
        "Apple", "Orange", "Banana", "Cherry", "Mango", "Peach", "Berry", "Lemon", "Melon", "Grape", "Papaya",
        "Falcon", "Tiger", "Eagle", "Dolphin", "Panda", "Rabbit", "Koala", "Jaguar", "Leopard", "Badger",
        "Planet", "Rocket", "Comet", "Galaxy", "Cosmos", "Meteor", "Saturn", "Jupiter", "Apollo",
        "Silver", "Golden", "Bronze", "Cobalt", "Quartz", "Emerald", "Diamond", "Amber", "Velvet",
        "Castle", "Bridge", "Anchor", "Beacon", "Shield", "Summit", "Forest", "Breeze", "Canyon", "Willow", "Harbor",
        "Guitar", "Canvas", "Pencil", "Lantern", "Compass", "Pocket", "Camera", "Mirror", "Puzzle", "River",
        "Ocean", "Meadow", "Valley", "Timber", "Spark", "Shadow", "Horizon", "Clover", "Zenith", "Bliss"
    )

    private val MEMORABLE_SYMBOLS = charArrayOf('@', '#', '$', '!', '%', '&', '*')

    private val secureRandom = SecureRandom()

    enum class Strength(val label: String) {
        VERY_WEAK("Very Weak"),
        WEAK("Weak"),
        MEDIUM("Medium"),
        STRONG("Strong"),
        VERY_STRONG("Very Strong")
    }

    data class GeneratorOptions(
        val length: Int = 16,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeDigits: Boolean = true,
        val includeSymbols: Boolean = true,
        val excludeAmbiguous: Boolean = false
    )

    /**
     * Generates a friendly, memorable password in the requested format (e.g., Apple@33).
     * Satisfies all policy requirements (Uppercase, Lowercase, Number, Special Character, 8+ chars).
     */
    fun generateMemorable(): String {
        val word = MEMORABLE_WORDS[secureRandom.nextInt(MEMORABLE_WORDS.size)]
        val symbol = MEMORABLE_SYMBOLS[secureRandom.nextInt(MEMORABLE_SYMBOLS.size)]
        val number = 10 + secureRandom.nextInt(90) // 10..99
        return "$word$symbol$number"
    }

    /**
     * Default generator: generates memorable passwords like 'Apple@33'
     */
    fun generate(): String {
        return generateMemorable()
    }

    fun generate(
        length: Int = 16,
        useUppercase: Boolean = true,
        useLowercase: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true,
        avoidAmbiguous: Boolean = false
    ): String {
        return generate(
            GeneratorOptions(
                length = length,
                includeUppercase = useUppercase,
                includeLowercase = useLowercase,
                includeDigits = useDigits,
                includeSymbols = useSymbols,
                excludeAmbiguous = avoidAmbiguous
            )
        )
    }

    fun generate(options: GeneratorOptions): String {
        val charPool = StringBuilder()

        var lower = LOWERCASE
        var upper = UPPERCASE
        var digits = DIGITS
        var symbols = SYMBOLS

        if (options.excludeAmbiguous) {
            lower = lower.filter { it !in AMBIGUOUS }
            upper = upper.filter { it !in AMBIGUOUS }
            digits = digits.filter { it !in AMBIGUOUS }
        }

        val requiredChars = mutableListOf<Char>()

        if (options.includeLowercase && lower.isNotEmpty()) {
            charPool.append(lower)
            requiredChars.add(lower[secureRandom.nextInt(lower.length)])
        }
        if (options.includeUppercase && upper.isNotEmpty()) {
            charPool.append(upper)
            requiredChars.add(upper[secureRandom.nextInt(upper.length)])
        }
        if (options.includeDigits && digits.isNotEmpty()) {
            charPool.append(digits)
            requiredChars.add(digits[secureRandom.nextInt(digits.length)])
        }
        if (options.includeSymbols && symbols.isNotEmpty()) {
            charPool.append(symbols)
            requiredChars.add(symbols[secureRandom.nextInt(symbols.length)])
        }

        if (charPool.isEmpty()) {
            charPool.append(lower.ifEmpty { LOWERCASE })
        }

        val result = StringBuilder()
        // Add guaranteed chars first
        result.append(requiredChars.joinToString(""))

        // Fill the rest
        val remainingLength = (options.length - result.length).coerceAtLeast(0)
        for (i in 0 until remainingLength) {
            val randomIndex = secureRandom.nextInt(charPool.length)
            result.append(charPool[randomIndex])
        }

        // Shuffle the characters so required chars aren't always at the front
        val charArray = result.toString().toCharArray()
        for (i in charArray.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = charArray[i]
            charArray[i] = charArray[j]
            charArray[j] = temp
        }

        return String(charArray)
    }

    fun estimateStrength(password: String): Strength {
        return evaluateStrength(password).first
    }

    fun evaluateStrength(password: String): Pair<Strength, Float> {
        if (password.isEmpty()) return Pair(Strength.VERY_WEAK, 0f)

        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        if (poolSize == 0) poolSize = 10

        // Entropy in bits = length * log2(poolSize)
        val entropy = password.length * (Math.log(poolSize.toDouble()) / Math.log(2.0))

        val score = (entropy / 80.0).coerceIn(0.0, 1.0).toFloat()

        val strength = when {
            entropy < 28 -> Strength.VERY_WEAK
            entropy < 45 -> Strength.WEAK
            entropy < 65 -> Strength.MEDIUM
            entropy < 85 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }

        return Pair(strength, score)
    }
}
