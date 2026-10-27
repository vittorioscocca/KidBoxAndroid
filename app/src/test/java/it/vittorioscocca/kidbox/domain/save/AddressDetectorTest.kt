package it.vittorioscocca.kidbox.domain.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressDetectorTest {
    @Test fun italiano() {
        assertEquals("Via Roma 12, 20121 Milano", AddressDetector.firstAddress("Ci vediamo in Via Roma 12, 20121 Milano alle 5"))
        assertEquals("via dei Mille, 4/B", AddressDetector.firstAddress("la festa è in via dei Mille, 4/B domani"))
    }
    @Test fun inglese() {
        assertEquals("221B Baker Street", AddressDetector.firstAddress("Meet at 221B Baker Street"))
    }
    @Test fun nessuno() {
        assertNull(AddressDetector.firstAddress("Compra il latte e il pane"))
        assertNull(AddressDetector.firstAddress("passa per via di lì"))
    }
}
