package it.vittorioscocca.kidbox.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La cassaforte v2 deve aprire ciò che scrive la web app. Il vettore è stato
 * SCRITTO dalla web app (familyKeyVault.js) il 09/11/2026 ed è lo stesso dei
 * test iOS: se fallisce, una costante non è più identica. Correggere il codice,
 * non il vettore.
 */
class FamilyKeyVaultCryptoTest {

    private val c = FamilyKeyVaultCrypto
    private val uid = "MeSf31GHULiGLS7KDyJleKMPSrZp"
    private val familyId = "4A3DC5FF-2036-43E0-93AA-E81C7083D875"
    private val code = "S62Z-TC4J-G5QR-F8W7-EWBJ-HH1C"
    private val familyKeyB64 = "HCb/oTP5dnV/7a6FzxnpyHMru4zA6XfjempmUyeohqY="
    private val rkId = "QjXYkcyX6Mgty8b86_NKjQ"
    private val slotSalt = "tZNue8zZv4R320I9beHVMA=="
    private val slot = FamilyKeyVaultCrypto.Sealed(
        "JueafNLfYA9ZsjlHcN21c5uh02a+rWk70rQYdSY+4Io=", "iWBNJdq4RvPVM6yK", "vx7npyQiM+gFYcOUFsY+zQ==",
    )
    private val v2 = FamilyKeyVaultCrypto.Sealed(
        "oTfoQwQX36GN6S1yNIGDMDY570RaePneyGL+TN2YeYQ=", "F+/PfljfjEYPM8QM", "qseWI3nQQERDTMWwUhSzAw==",
    )

    private fun webRk() = c.openRecoverySlot(uid, code, slotSalt, slot, rkId)

    @Test fun opensWebSlotWithRecoveryCode() {
        val rk = webRk()
        assertEquals(32, rk.size)
        assertEquals(rkId, c.rkId(rk))
    }

    @Test fun opensWebV2FamilyKey() {
        assertEquals(familyKeyB64, c.b64(c.openFamilyKeyV2(uid, familyId, v2, webRk())!!))
    }

    @Test fun codeIsNormalized() {
        assertEquals("S62ZTC4JG5QRF8W7EWBJHH1C", c.normalizeRecoveryCode(" s62z tc4j-g5qr f8w7 ewbj hhLc "))
        assertEquals("000011111111000011112222", c.normalizeRecoveryCode("OOOO-IIII-LLLL-0000-1111-2222"))
        assertNull(c.normalizeRecoveryCode("UUUU-UUUU-UUUU-UUUU-UUUU-UUUU"))
        c.openRecoverySlot(uid, "s62z tc4j g5qr f8w7 ewbj hhLc", slotSalt, slot, rkId)
    }

    @Test(expected = FamilyKeyVaultCrypto.WrongSecretException::class)
    fun wrongCodeIsRejected() {
        c.openRecoverySlot(uid, "S62Z-TC4J-G5QR-F8W7-EWBJ-HH1D", slotSalt, slot, rkId)
    }

    @Test(expected = FamilyKeyVaultCrypto.WrongSecretException::class)
    fun otherUidIsRejected() {
        c.openRecoverySlot("altro", code, slotSalt, slot, null)
    }

    @Test fun aadBindsFamilyAndUid() {
        val rk = webRk()
        assertNull(c.openFamilyKeyV2(uid, "altra-famiglia", v2, rk))
        assertNull(c.openFamilyKeyV2("altro", familyId, v2, rk))
    }

    @Test fun roundTrip() {
        val newCode = c.generateRecoveryCode()
        assertEquals(29, newCode.length)
        assertNotNull(c.normalizeRecoveryCode(newCode))
        val rk = c.newRk()
        val (salt, sealed) = c.sealRecoverySlot(uid, newCode, rk)
        assertArrayEquals(rk, c.openRecoverySlot(uid, newCode, salt, sealed, c.rkId(rk)))
        val fk = c.unb64(familyKeyB64)
        assertArrayEquals(fk, c.openFamilyKeyV2(uid, familyId, c.sealFamilyKeyV2(uid, familyId, fk, rk), rk))
    }
}
