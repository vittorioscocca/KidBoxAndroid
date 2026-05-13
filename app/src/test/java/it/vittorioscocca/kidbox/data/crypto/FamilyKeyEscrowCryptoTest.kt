package it.vittorioscocca.kidbox.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyKeyEscrowCryptoTest {

    @Test
    fun escrowWrap_roundtrip_matchesInviteCrypto() {
        val uid = "testUid"
        val familyId = "testFamily"
        val wrap = InviteCrypto.deriveEscrowWrapKey(uid, familyId)
        assertEquals(32, wrap.size)
        val familyKey = InviteCrypto.randomBytes(32)
        val w = InviteCrypto.wrapFamilyKey(familyKey, wrap)
        val plain = InviteCrypto.unwrapFamilyKey(w.cipher, w.nonce, w.tag, wrap)
        assertArrayEquals(familyKey, plain)
    }
}
