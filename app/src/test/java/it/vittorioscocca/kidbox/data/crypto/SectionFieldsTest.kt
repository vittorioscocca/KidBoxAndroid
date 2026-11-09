package it.vittorioscocca.kidbox.data.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

/**
 * Regole della cifratura delle sezioni (`internal/cifratura-sezioni.md`) senza
 * chiave vera: la «cifratura» qui è un prefisso reversibile.
 */
class SectionFieldsTest {

    private val encrypt: (String) -> String? = { "ENC:$it" }
    private val decrypt: (String) -> String? = { it.removePrefix("ENC:").takeIf { _ -> it.startsWith("ENC:") } }
    private val missingKey: (String) -> String? = { null }

    private fun sealed(vararg values: Pair<String, String>): String =
        "ENC:" + JSONObject(values.toMap()).toString()

    @Test
    fun chiaroNonVuotoVince() {
        val data = mapOf(
            "title" to "Spesa nuova",
            "notes" to "",
            "secure" to sealed("title" to "Spesa vecchia", "notes" to "nota cifrata"),
        )
        val out = SectionFields.resolve("expenses", data, decrypt)
        assertEquals("Spesa nuova", out["title"])
        assertEquals("nota cifrata", out["notes"])
    }

    @Test
    fun campiVuotiPresiDaSecure() {
        val data = mapOf<String, Any?>(
            "name" to "",
            "licensePlate" to null,
            "currentKm" to 12000,
            "secure" to sealed("name" to "Panda", "licensePlate" to "AB123CD"),
            "secureV" to 1,
        )
        val out = SectionFields.resolve("vehicles", data, decrypt)
        assertEquals("Panda", out["name"])
        assertEquals("AB123CD", out["licensePlate"])
        assertEquals(12000, out["currentKm"])
    }

    @Test
    fun chiaveMancanteLasciaIlChiaro() {
        val data = mapOf(
            "title" to "",
            "notes" to "",
            "secure" to sealed("title" to "Cena"),
        )
        val out = SectionFields.resolve("expenses", data, missingKey)
        assertSame(data, out)
        assertEquals("", out["title"])
        assertTrue(SectionFields.isSealed(out))
    }

    @Test
    fun blobIllegibileLasciaIlChiaro() {
        val data = mapOf("title" to "", "secure" to "ENC:non json")
        assertSame(data, SectionFields.resolve("expenses", data, decrypt))
    }

    @Test
    fun senzaSecureNienteDecifratura() {
        val data = mapOf("title" to "Pranzo")
        var called = false
        val out = SectionFields.resolve("expenses", data) { called = true; null }
        assertSame(data, out)
        assertFalse(called)
    }

    @Test
    fun sealSvuotaICampiEMetteSecure() {
        val data = mapOf<String, Any?>(
            "title" to "Dentista",
            "notes" to null,
            "amount" to 80.0,
            "updatedBy" to "uid",
        )
        val out = SectionFields.seal("expenses", data, encrypt)
        assertNotNull(out)
        out!!
        assertEquals("", out["title"])
        assertEquals("", out["notes"])
        assertEquals(80.0, out["amount"])
        assertEquals("uid", out["updatedBy"])
        assertEquals(1, out["secureV"])
        // Il giro completo torna ai valori, senza i nulli.
        val back = SectionFields.resolve("expenses", out, decrypt)
        assertEquals("Dentista", back["title"])
        assertEquals("", back["notes"])
        assertEquals("{\"title\":\"Dentista\"}", decrypt(out["secure"] as String))
    }

    @Test
    fun sealSenzaChiaveRestituisceNull() {
        assertNull(SectionFields.seal("expenses", mapOf("title" to "x"), missingKey))
    }

    @Test
    fun ricifraturaToccaSoloSecureECampi() {
        val raw = mapOf<String, Any?>(
            "title" to "Tagliando",
            "notes" to "",
            "garageName" to "",
            "secure" to sealed("notes" to "olio"),
            "updatedAt" to 123L,
        )
        assertTrue(SectionFields.hasPlain("vehicleEvents", raw))
        val resolved = SectionFields.resolve("vehicleEvents", raw, decrypt)
        val payload = SectionFields.reencryptPayload("vehicleEvents", resolved, encrypt)!!
        assertEquals(setOf("title", "garageName", "notes", "secure", "secureV"), payload.keys)
        val json = JSONObject(decrypt(payload["secure"] as String)!!)
        assertEquals("Tagliando", json.getString("title"))
        assertEquals("olio", json.getString("notes"))
        assertFalse(json.has("garageName"))
        assertFalse(SectionFields.hasPlain("vehicleEvents", payload))
    }
}
