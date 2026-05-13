package it.vittorioscocca.kidbox.data.passwords.otp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpGenerator {
    fun generateTOTP(config: OtpConfig, nowMillis: Long = System.currentTimeMillis()): String {
        val keyBytes = Base32Decoder.decode(config.secret.uppercase())
        val period = config.period.coerceAtLeast(1)
        val digits = config.digits.coerceIn(4, 10)
        val counter = nowMillis / 1000L / period
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("Hmac${config.algorithm.uppercase()}")
        mac.init(SecretKeySpec(keyBytes, "RAW"))
        val hash = mac.doFinal(counterBytes)
        val offset = hash.last().toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val modulo = 10.0.pow(digits.toDouble()).toInt()
        return (code % modulo).toString().padStart(digits, '0')
    }
}

object Base32Decoder {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(raw: String): ByteArray {
        val cleaned = raw.filterNot { it == '=' || it.isWhitespace() }.uppercase()
        require(cleaned.isNotEmpty()) { "Base32 secret empty" }
        var bits = 0
        var value = 0
        val out = ArrayList<Byte>()
        for (ch in cleaned) {
            val idx = ALPHABET.indexOf(ch)
            require(idx >= 0) { "Invalid Base32 char: $ch" }
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((value shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
