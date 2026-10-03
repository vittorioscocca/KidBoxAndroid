package it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stessi vettori provati sul JS della web app: le tre implementazioni devono coincidere. */
class PaymentCardFormatTest {

    @Test
    fun network() {
        assertEquals(PaymentCardNetwork.VISA, PaymentCardNetwork.detect("4111 1111 1111 1111"))
        assertEquals(PaymentCardNetwork.AMEX, PaymentCardNetwork.detect("378282246310005"))
        assertEquals(PaymentCardNetwork.MASTERCARD, PaymentCardNetwork.detect("5555555555554444"))
        assertEquals(PaymentCardNetwork.MASTERCARD, PaymentCardNetwork.detect("2223003122003222"))
        assertEquals(PaymentCardNetwork.MAESTRO, PaymentCardNetwork.detect("6759649826438453"))
        assertEquals(PaymentCardNetwork.OTHER, PaymentCardNetwork.detect(""))
    }

    @Test
    fun groupingAndMask() {
        assertEquals("4111 1111 1111 1111", PaymentCardFormat.grouped("4111111111111111"))
        assertEquals("3782 822463 10005", PaymentCardFormat.grouped("378282246310005"))
        assertEquals("•••• •••• •••• 1111", PaymentCardFormat.masked("4111111111111111"))
    }

    @Test
    fun luhn() {
        assertTrue(PaymentCardFormat.passesLuhn("4111111111111111"))
        assertTrue(PaymentCardFormat.passesLuhn("378282246310005"))
        assertFalse(PaymentCardFormat.passesLuhn("4111111111111112"))
        assertFalse(PaymentCardFormat.passesLuhn("41111"))
    }

    @Test
    fun expiry() {
        assertEquals("12/28", PaymentCardFormat.expiryInput("1228"))
        assertEquals("12", PaymentCardFormat.expiryInput("12"))
        assertTrue(PaymentCardFormat.isValidExpiry("12/28"))
        assertFalse(PaymentCardFormat.isValidExpiry("13/28"))
        val oct2026 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 3) }
        assertTrue(PaymentCardFormat.isExpired("09/26", oct2026))
        assertFalse(PaymentCardFormat.isExpired("10/26", oct2026))
    }

    @Test
    fun iban() {
        assertTrue(PaymentCardFormat.isValidIban("IT60 X054 2811 1010 0000 0123 456"))
        assertTrue(PaymentCardFormat.isValidIban("GB82WEST12345698765432"))
        assertFalse(PaymentCardFormat.isValidIban("IT60X0542811101000000123457"))
        assertEquals("IT60 X054 2811 1010 0000 0123 456", PaymentCardFormat.ibanGrouped("it60x0542811101000000123456"))
    }
}
