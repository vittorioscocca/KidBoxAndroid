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

    // ── Secondo giro ────────────────────────────────────────────────────────

    @Test
    fun campiStellaSiCancellanoGliAltriVannoVuoti() {
        val data = mapOf<String, Any?>(
            "reason" to "Febbre",
            "asNeededDrugsJson" to "[\"Tachipirina\"]",
            "travelDetailsData" to "eyJhIjoxfQ==",
        )
        val out = SectionFields.seal("medicalVisits", data, encrypt)!!
        assertEquals("", out["reason"])
        assertEquals("", out["doctorName"])
        assertSame(SectionFields.Deleted, out["asNeededDrugsJson"])
        assertSame(SectionFields.Deleted, out["travelDetailsData"])
        assertSame(SectionFields.Deleted, out["prescribedExamsJson"])
        val json = JSONObject(decrypt(out["secure"] as String)!!)
        assertEquals("[\"Tachipirina\"]", json.getString("asNeededDrugsJson"))
        assertEquals("eyJhIjoxfQ==", json.getString("travelDetailsData"))
    }

    @Test
    fun ricifraturaCancellaICampiStella() {
        val raw = mapOf<String, Any?>("reason" to "Controllo", "prescribedExamsJson" to "[]")
        val resolved = SectionFields.resolve("medicalVisits", raw, decrypt)
        val payload = SectionFields.reencryptPayload("medicalVisits", resolved, encrypt, deleteValue = "DEL")!!
        assertEquals("", payload["reason"])
        assertEquals("DEL", payload["prescribedExamsJson"])
        assertEquals("DEL", payload["travelDetailsJson"])
        assertEquals("[]", JSONObject(decrypt(payload["secure"] as String)!!).getString("prescribedExamsJson"))
    }

    @Test
    fun campoStellaAssenteValeVuotoInLettura() {
        val data = mapOf<String, Any?>(
            "reason" to "",
            "secure" to sealed("reason" to "Tosse", "travelDetailsJson" to "{\"dest\":\"Lisbona\"}"),
        )
        val out = SectionFields.resolve("medicalVisits", data, decrypt)
        assertEquals("Tosse", out["reason"])
        assertEquals("{\"dest\":\"Lisbona\"}", out["travelDetailsJson"])
    }

    @Test
    fun scritturaParzialePrendeIMancantiDalDocumento() {
        // Android non scrive i dettagli di viaggio: arrivano dal documento esistente.
        val payload = mapOf<String, Any?>("reason" to "Nuovo motivo", "notes" to null, "cost" to 50.0)
        val existing = mapOf<String, Any?>(
            "reason" to "Vecchio motivo",
            "notes" to "nota vecchia",
            "travelDetailsJson" to "{\"x\":1}",
            "doctorName" to "",
        )
        val merged = SectionFields.mergeMissing("medicalVisits", payload, existing)
        assertEquals("Nuovo motivo", merged["reason"])
        // Portato dalla scrittura (anche nullo: svuotato dall'utente) → non si riprende.
        assertNull(merged["notes"])
        assertEquals("{\"x\":1}", merged["travelDetailsJson"])
        assertFalse("doctorName" in merged)
        assertEquals(50.0, merged["cost"])
        val json = JSONObject(decrypt(SectionFields.seal("medicalVisits", merged, encrypt)!!["secure"] as String)!!)
        assertEquals("Nuovo motivo", json.getString("reason"))
        assertEquals("{\"x\":1}", json.getString("travelDetailsJson"))
        assertFalse(json.has("notes"))
    }

    @Test
    fun vacciniTengonoNomeMedicoELuogo() {
        val payload = mapOf<String, Any?>("commercialName" to "Hexyon", "notes" to "ok")
        val existing = mapOf<String, Any?>("name" to "Esavalente", "doctorName" to "Rossi", "location" to "ASL")
        val merged = SectionFields.mergeMissing("vaccines", payload, existing)
        val out = SectionFields.seal("vaccines", merged, encrypt)!!
        assertEquals("", out["name"])
        assertEquals("", out["location"])
        val back = SectionFields.resolve("vaccines", out, decrypt)
        assertEquals("Esavalente", back["name"])
        assertEquals("Rossi", back["doctorName"])
        assertEquals("ASL", back["location"])
        assertEquals("Hexyon", back["commercialName"])
    }

    @Test
    fun scritturaCompletaNonHaMancanti() {
        val payload = mapOf<String, Any?>("title" to "Pane", "notes" to null)
        assertTrue(SectionFields.missingFields("todos", payload).isEmpty())
        assertSame(payload, SectionFields.mergeMissing("todos", payload, mapOf("notes" to "x")))
        assertEquals(listOf("notes"), SectionFields.missingFields("todos", mapOf("title" to "Pane")))
    }

    @Test
    fun scritturaSenzaCampiDellaTabellaNonSiSigilla() {
        assertFalse(SectionFields.touchesSealedFields("children", mapOf("birthDate" to 1L, "weightKg" to 12.0)))
        assertTrue(SectionFields.touchesSealedFields("children", mapOf("name" to null)))
    }

    @Test
    fun secureIllegibileNonSiSovrascrive() {
        val raw = mapOf<String, Any?>("name" to "", "secure" to "altra-chiave")
        val resolved = SectionFields.resolve("children", raw, decrypt)
        assertTrue(SectionFields.isUnreadable("children", raw, resolved))
        val ok = mapOf<String, Any?>("name" to "", "secure" to sealed("name" to "Marco"))
        assertFalse(SectionFields.isUnreadable("children", ok, SectionFields.resolve("children", ok, decrypt)))
    }

    @Test
    fun tabellaEstesa() {
        assertEquals(listOf("name"), SectionFields.fieldsOf("children"))
        assertEquals(listOf("title", "notes"), SectionFields.fieldsOf("todos"))
        assertEquals(listOf("name"), SectionFields.fieldsOf("todoLists"))
        assertEquals(listOf("title", "notes", "location"), SectionFields.fieldsOf("calendarEvents"))
        assertTrue(SectionFields.fieldsOf("vaccines").containsAll(listOf("name", "doctorName", "location")))
        assertEquals(12, SectionFields.fieldsOf("medicalVisits").size)
    }

    @Test
    fun riaperturaCalendarioRimetteIlChiaroECancellaSecure() {
        val raw = mapOf<String, Any?>(
            "title" to "",
            "notes" to "",
            "location" to "",
            "secure" to sealed("title" to "Recita", "location" to "Teatro"),
            "secureV" to 1,
        )
        val resolved = SectionFields.resolve("calendarEvents", raw, decrypt)
        val payload = SectionFields.reopenPayload("calendarEvents", resolved, deleteValue = "DEL")
        assertEquals(
            mapOf("title" to "Recita", "location" to "Teatro", "secure" to "DEL", "secureV" to "DEL"),
            payload,
        )
    }
}
