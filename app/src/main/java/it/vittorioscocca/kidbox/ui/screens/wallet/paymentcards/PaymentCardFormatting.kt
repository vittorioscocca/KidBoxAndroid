package it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards

import java.util.Calendar

/**
 * Circuito, controlli e formattazione delle carte di pagamento. Stessa logica
 * di `PaymentCardFormatting.swift` (iOS) e `paymentCardFormat.js` (web): se
 * cambia qui, cambia anche lì.
 */
enum class PaymentCardNetwork(val displayName: String?) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    MAESTRO("Maestro"),
    DISCOVER("Discover"),
    DINERS("Diners Club"),
    JCB("JCB"),
    UNIONPAY("UnionPay"),

    /** Nome localizzato a parte («Carta»). */
    OTHER(null),
    ;

    companion object {
        fun detect(number: String): PaymentCardNetwork {
            val d = number.filter { it.isDigit() }
            if (d.isEmpty()) return OTHER
            fun prefix(n: Int): Int = d.take(n).takeIf { it.length == n }?.toIntOrNull() ?: -1
            return when {
                d[0] == '4' -> VISA
                prefix(2) == 34 || prefix(2) == 37 -> AMEX
                prefix(2) in 51..55 || prefix(4) in 2221..2720 -> MASTERCARD
                prefix(4) == 6011 || prefix(2) == 65 || prefix(3) in 644..649 -> DISCOVER
                prefix(4) in 3528..3589 -> JCB
                prefix(2) == 36 || prefix(2) == 38 || prefix(2) == 39 || prefix(3) in 300..305 -> DINERS
                prefix(2) == 62 -> UNIONPAY
                prefix(2) == 50 || prefix(2) in 56..69 -> MAESTRO
                else -> OTHER
            }
        }
    }
}

object PaymentCardFormat {

    /** Solo cifre, massimo 19. */
    fun digits(raw: String): String = raw.filter { it.isDigit() }.take(19)

    /** A gruppi: 4-6-5 per American Express, 4-4-4-4(-3) per gli altri. */
    fun grouped(number: String): String {
        val d = digits(number)
        val sizes = if (PaymentCardNetwork.detect(d) == PaymentCardNetwork.AMEX) listOf(4, 6, 5) else listOf(4, 4, 4, 4, 3)
        val out = mutableListOf<String>()
        var rest = d
        for (size in sizes) {
            if (rest.isEmpty()) break
            out += rest.take(size)
            rest = rest.drop(size)
        }
        return out.joinToString(" ")
    }

    /** Restano visibili solo le ultime 4 cifre. */
    fun masked(number: String): String {
        val d = digits(number)
        if (d.length <= 4) return d
        return "•••• •••• •••• " + d.takeLast(4)
    }

    /** Controllo di Luhn: il form avvisa ma non blocca. */
    fun passesLuhn(number: String): Boolean {
        val d = digits(number)
        if (d.length < 12) return false
        var sum = 0
        d.reversed().forEachIndexed { i, ch ->
            var n = ch.digitToInt()
            if (i % 2 == 1) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
        }
        return sum % 10 == 0
    }

    /** Normalizza quello che si digita in `MM/AA`, inserendo la barra da sé. */
    fun expiryInput(raw: String): String {
        val d = raw.filter { it.isDigit() }.take(4)
        return if (d.length > 2) d.take(2) + "/" + d.drop(2) else d
    }

    fun isValidExpiry(expiry: String): Boolean {
        val (m, _) = monthYear(expiry) ?: return false
        return m in 1..12
    }

    /** Scaduta = finito il mese indicato. */
    fun isExpired(expiry: String, now: Calendar = Calendar.getInstance()): Boolean {
        val (m, y) = monthYear(expiry) ?: return false
        if (m !in 1..12) return false
        val curY = now.get(Calendar.YEAR) % 100
        val curM = now.get(Calendar.MONTH) + 1
        return y < curY || (y == curY && m < curM)
    }

    private fun monthYear(expiry: String): Pair<Int, Int>? {
        val d = expiry.filter { it.isDigit() }
        if (d.length != 4) return null
        val m = d.take(2).toIntOrNull() ?: return null
        val y = d.takeLast(2).toIntOrNull() ?: return null
        return m to y
    }

    /** Maiuscolo, senza spazi, massimo 34 caratteri. */
    fun ibanCompact(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it.isDigit() }.take(34)

    fun ibanGrouped(raw: String): String = ibanCompact(raw).chunked(4).joinToString(" ")

    /** ISO 13616 (mod 97): anche qui il form avvisa, non blocca. */
    fun isValidIban(raw: String): Boolean {
        val c = ibanCompact(raw)
        if (c.length < 15) return false
        if (!c.take(2).all { it in 'A'..'Z' } || !c.drop(2).take(2).all { it.isDigit() }) return false
        val rearranged = c.drop(4) + c.take(4)
        var remainder = 0
        for (ch in rearranged) {
            val value = if (ch.isDigit()) ch.digitToInt() else ch - 'A' + 10
            for (digit in value.toString()) {
                remainder = (remainder * 10 + digit.digitToInt()) % 97
            }
        }
        return remainder == 1
    }
}

/** Palette fissa, uguale su iOS, Android e web. */
object PaymentCardPalette {
    const val DEFAULT_HEX = "#1C1C1E"
    val all = listOf(
        "#1C1C1E", // nero
        "#0A3D91", // blu
        "#1B7F5A", // verde
        "#8E1B3A", // bordeaux
        "#B8860B", // oro
        "#6E6E73", // argento
        "#5856D6", // viola
        "#D2462E", // rosso
    )
}
