package it.vittorioscocca.kidbox.data.crypto

import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PasswordCryptoEngineTest {

    private fun key32(): ByteArray = ByteArray(32) { (it + 7).toByte() }

    @Test
    fun roundTrip_familyVisibility() {
        val key = key32()
        val plain = "Segreto super-mario-64!"
        val cipher = PasswordCryptoEngine.encrypt(plain, key, KBVisibilityScope.FAMILY, "user-a")
        val out = PasswordCryptoEngine.decrypt(cipher, key, KBVisibilityScope.FAMILY, "user-a", decryptingUid = "user-b")
        assertEquals(plain, out)
    }

    @Test
    fun roundTrip_onlyCreator_sameCreator() {
        val key = key32()
        val uid = "creator-uid"
        val plain = "TOTP-seed-or-password"
        val cipher = PasswordCryptoEngine.encrypt(plain, key, KBVisibilityScope.ONLY_CREATOR, uid)
        val out = PasswordCryptoEngine.decrypt(cipher, key, KBVisibilityScope.ONLY_CREATOR, uid, decryptingUid = uid)
        assertEquals(plain, out)
    }

    @Test
    fun onlyCreator_decryptDeniedForOtherUser() {
        val key = key32()
        val creator = "creator"
        val other = "other"
        val plain = "solo-io"
        val cipher = PasswordCryptoEngine.encrypt(plain, key, KBVisibilityScope.ONLY_CREATOR, creator)
        try {
            PasswordCryptoEngine.decrypt(cipher, key, KBVisibilityScope.ONLY_CREATOR, creator, decryptingUid = other)
            fail("expected NotCreatorForPrivateEntry")
        } catch (e: PasswordCryptoEngine.PasswordCryptoException) {
            assertTrue(e is PasswordCryptoEngine.PasswordCryptoException.NotCreatorForPrivateEntry)
        }
    }
}
