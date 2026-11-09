package io.github.byluismoya.cryptcore.core

import java.security.SecureRandom

object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    data class Options(
        val length: Int = 20,
        val upper: Boolean = true,
        val lower: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidAmbiguous: Boolean = false,
    )

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/~"
    private const val AMBIGUOUS = "Il1O0o|"

    private val random = SecureRandom()

    /** Guarantees at least one character of every selected class, then shuffles (Fisher–Yates). */
    fun generate(options: Options): String {
        val pools = buildList {
            if (options.upper) add(UPPER)
            if (options.lower) add(LOWER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.ifEmpty { listOf(LOWER) }
            .map { pool -> if (options.avoidAmbiguous) pool.filterNot { it in AMBIGUOUS } else pool }

        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val all = pools.joinToString("")
        val chars = CharArray(length)
        pools.forEachIndexed { i, pool -> chars[i] = pool[random.nextInt(pool.length)] }
        for (i in pools.size until length) chars[i] = all[random.nextInt(all.length)]
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }
}
