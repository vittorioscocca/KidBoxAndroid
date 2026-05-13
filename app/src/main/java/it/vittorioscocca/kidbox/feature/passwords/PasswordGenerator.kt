package it.vittorioscocca.kidbox.feature.passwords

import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

data class PasswordGeneratorOptions(
    var length: Int = 18,
    var includeUppercase: Boolean = true,
    var includeLowercase: Boolean = true,
    var includeNumbers: Boolean = true,
    var includeSymbols: Boolean = true,
    var excludeAmbiguous: Boolean = true,
)

object PasswordGenerator {

    private val rng = SecureRandom()

    private const val LOWER_ALL = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER_ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS_ALL = "0123456789"
    private const val SYMBOLS_ALL = "!@#\$%^&*()-_=+[]{}|;:,.<>?/~`"
    private val AMBIGUOUS = setOf('0', 'O', '1', 'l', 'I')

    @JvmStatic
    fun make(length: Int = 18): String {
        val o = PasswordGeneratorOptions(length = length)
        return generate(o)
    }

    @JvmStatic
    fun generate(raw: PasswordGeneratorOptions = PasswordGeneratorOptions()): String {
        var options = raw.copy(length = max(8, min(64, raw.length)))

        val pools = buildList {
            if (options.includeLowercase) add(filtered(LOWER_ALL, options.excludeAmbiguous))
            if (options.includeUppercase) add(filtered(UPPER_ALL, options.excludeAmbiguous))
            if (options.includeNumbers) add(filtered(DIGITS_ALL, options.excludeAmbiguous))
            if (options.includeSymbols) add(SYMBOLS_ALL)
        }.filter { it.isNotEmpty() }

        val effectivePools = if (pools.isEmpty()) {
            val fallback = PasswordGeneratorOptions(length = options.length)
            listOf(
                filtered(LOWER_ALL, fallback.excludeAmbiguous),
                filtered(UPPER_ALL, fallback.excludeAmbiguous),
                filtered(DIGITS_ALL, fallback.excludeAmbiguous),
                SYMBOLS_ALL,
            ).filter { it.isNotEmpty() }
        } else {
            pools
        }

        val union = effectivePools.joinToString("")
        if (union.isEmpty()) return make(options.length)

        val chars = ArrayList<Char>(options.length)
        for (p in effectivePools) {
            if (p.isNotEmpty()) chars.add(randomChar(p))
        }
        while (chars.size < options.length) {
            chars.add(randomChar(union))
        }
        chars.shuffle(rng)
        return chars.take(options.length).joinToString("")
    }

    private fun filtered(s: String, excludeAmbiguous: Boolean): String {
        if (!excludeAmbiguous) return s
        return s.filter { it !in AMBIGUOUS }
    }

    private fun randomChar(s: String): Char = s[rng.nextInt(s.length)]
}
