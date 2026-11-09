package io.github.byluismoya.cryptcore.core

import kotlin.math.log2

enum class Strength(val level: Int, val label: String) {
    Empty(0, ""),
    VeryWeak(1, "Muy débil"),
    Weak(1, "Débil"),
    Fair(2, "Aceptable"),
    Strong(3, "Fuerte"),
    VeryStrong(4, "Muy fuerte"),
}

/** Cheap offline estimate: character-pool entropy, discounted for runs like "aaaa" or "1234". */
object PasswordStrength {
    private val common = setOf(
        "password", "password1", "123456", "12345678", "123456789", "1234567890", "qwerty",
        "qwertyuiop", "abc123", "111111", "000000", "123123", "654321", "admin", "letmein",
        "iloveyou", "contraseña", "contrasena", "secreto", "teamo",
    )

    fun bits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 33
        var effective = 1.0
        for (i in 1 until password.length) {
            val step = password[i].code - password[i - 1].code
            effective += if (step in -1..1) 0.25 else 1.0
        }
        return effective * log2(pool.coerceAtLeast(2).toDouble())
    }

    fun estimate(password: String): Strength {
        if (password.isEmpty()) return Strength.Empty
        if (password.length < 6 || password.lowercase() in common) return Strength.VeryWeak
        val b = bits(password)
        return when {
            b < 28 -> Strength.VeryWeak
            b < 40 -> Strength.Weak
            b < 60 -> Strength.Fair
            b < 85 -> Strength.Strong
            else -> Strength.VeryStrong
        }
    }
}
