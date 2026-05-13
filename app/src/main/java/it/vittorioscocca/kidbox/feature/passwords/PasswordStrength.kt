package it.vittorioscocca.kidbox.feature.passwords

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

enum class PasswordStrengthLevel {
    VERY_WEAK,
    WEAK,
    FAIR,
    STRONG,
    VERY_STRONG,
    ;

    /** Breve etichetta UI (italiano). */
    fun labelIt(): String = when (this) {
        VERY_WEAK -> "Molto debole"
        WEAK -> "Debole"
        FAIR -> "Discreta"
        STRONG -> "Forte"
        VERY_STRONG -> "Molto forte"
    }
}

data class PasswordStrengthResult(
    val level: PasswordStrengthLevel,
    /** 0…1 per la barra. */
    val fillFraction: Double,
    val estimatedBits: Double,
)

object PasswordStrength {

    fun evaluate(password: String): PasswordStrengthResult {
        if (password.isEmpty()) {
            return PasswordStrengthResult(PasswordStrengthLevel.VERY_WEAK, 0.0, 0.0)
        }
        val L = password.length
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var pool = 0
        if (hasLower) pool += 26
        if (hasUpper) pool += 26
        if (hasDigit) pool += 10
        if (hasSymbol) pool += 33
        pool = max(pool, 2)

        val uniformBits = L * log2(pool.toDouble())
        val shannonPer = shannonEntropyPerChar(password)
        val shannonTotal = L * shannonPer

        var estimated = 0.55 * uniformBits + 0.45 * shannonTotal
        var penalty = 1.0
        penalty *= repeatingRunPenalty(password)
        penalty *= sequentialPenalty(password)
        penalty *= keyboardPenalty(password)
        penalty *= commonPasswordPenalty(password)
        if (L < 8) penalty *= 0.45
        else if (L < 12) penalty *= 0.82

        estimated *= penalty
        estimated = max(0.0, min(estimated, 160.0))

        val level = when {
            estimated < 18 -> PasswordStrengthLevel.VERY_WEAK
            estimated < 32 -> PasswordStrengthLevel.WEAK
            estimated < 48 -> PasswordStrengthLevel.FAIR
            estimated < 64 -> PasswordStrengthLevel.STRONG
            else -> PasswordStrengthLevel.VERY_STRONG
        }
        val fill = min(1.0, estimated / 80.0)
        return PasswordStrengthResult(level, fill, estimated)
    }

    private fun shannonEntropyPerChar(s: String): Double {
        if (s.isEmpty()) return 0.0
        val counts = HashMap<Char, Int>()
        for (ch in s) counts[ch] = (counts[ch] ?: 0) + 1
        val n = s.length.toDouble()
        var h = 0.0
        for (c in counts.values) {
            val p = c / n
            h -= p * log2(p)
        }
        return h
    }

    private fun repeatingRunPenalty(s: String): Double {
        if (s.length < 2) return 1.0
        var maxRun = 1
        var run = 1
        val arr = s.toCharArray()
        for (i in 1 until arr.size) {
            if (arr[i] == arr[i - 1]) {
                run++
                maxRun = max(maxRun, run)
            } else {
                run = 1
            }
        }
        if (maxRun >= s.length && s.length >= 4) return 0.25
        if (maxRun >= 4) return 0.65
        if (maxRun == 3) return 0.85
        return 1.0
    }

    private fun sequentialPenalty(s: String): Double {
        val lower = s.lowercase()
        if (containsLetterSequence(lower, 4, ascending = true)) return 0.72
        if (containsLetterSequence(lower, 4, ascending = false)) return 0.72
        if (containsDigitRun(s, 4, 1)) return 0.75
        return 1.0
    }

    private val letterIndex: Map<Char, Int> = ('a'..'z').mapIndexed { i, c -> c to i }.toMap()

    private fun containsLetterSequence(s: String, minLen: Int, ascending: Boolean): Boolean {
        val chars = s.toCharArray()
        var run = 1
        for (i in 1 until chars.size) {
            val a = letterIndex[chars[i - 1]]
            val b = letterIndex[chars[i]]
            if (a == null || b == null) {
                run = 1
                continue
            }
            val ok = if (ascending) b == a + 1 else b == a - 1
            if (ok) {
                run++
                if (run >= minLen) return true
            } else {
                run = 1
            }
        }
        return false
    }

    private fun containsDigitRun(s: String, len: Int, step: Int): Boolean {
        val arr = s.filter { it.isDigit() }.map { it.digitToInt() }
        if (arr.size < len) return false
        var run = 1
        for (i in 1 until arr.size) {
            if (arr[i] == arr[i - 1] + step) {
                run++
                if (run >= len) return true
            } else {
                run = 1
            }
        }
        return false
    }

    private val KEYBOARD_ROWS = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
        "1234567890",
    )

    private fun keyboardPenalty(s: String): Double {
        val t = s.lowercase()
        for (row in KEYBOARD_ROWS) {
            if (containsKeyboardRun(t, row, 4)) return 0.7
        }
        return 1.0
    }

    private fun containsKeyboardRun(s: String, row: String, minLen: Int): Boolean {
        val chars = s.toCharArray()
        val r = row.toCharArray()
        var idx = 0
        while (idx < chars.size) {
            val pos = r.indexOf(chars[idx])
            if (pos < 0) {
                idx++
                continue
            }
            var length = 1
            var ni = idx + 1
            var expected = pos + 1
            while (ni < chars.size && expected < r.size) {
                if (chars[ni] == r[expected]) {
                    length++
                    ni++
                    expected++
                } else {
                    break
                }
            }
            if (length >= minLen) return true
            idx++
        }
        return false
    }

    private val COMMON = setOf(
        "password", "password1", "123456", "12345678", "123456789", "qwerty", "abc123",
        "letmein", "welcome", "monkey", "dragon", "111111", "sunshine", "princess",
        "football", "iloveyou", "admin", "login", "master", "passw0rd", "654321",
    )

    private fun commonPasswordPenalty(s: String): Double {
        val t = s.lowercase()
        if (COMMON.contains(t)) return 0.15
        for (w in COMMON) {
            if (w.length >= 4 && t.contains(w)) return 0.45
        }
        return 1.0
    }
}
