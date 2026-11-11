package it.vittorioscocca.kidbox.data.remote

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le date dei documenti foto: iOS e web scrivono Timestamp (`takenAt`,
 * `createdAt`, `updatedAt`), le vecchie build Android numeri `...EpochMillis`.
 * Leggere solo i numeri dava a ogni foto di iOS la data della sincronizzazione.
 */
class PhotoVideoRemoteStoreTest {

    private val takenAt = 1_751_000_123_456L // un giorno di giugno 2025, con i millisecondi

    private fun ts(millis: Long) = Timestamp(Math.floorDiv(millis, 1000L), (Math.floorMod(millis, 1000L) * 1_000_000L).toInt())

    @Test
    fun `un Timestamp di iOS viene letto in millisecondi`() {
        val doc = mapOf<String, Any?>("takenAt" to ts(takenAt))
        assertEquals(takenAt, epochMillisOf(doc, "takenAt", "takenAtEpochMillis"))
    }

    @Test
    fun `il Timestamp vince sul numero delle vecchie build`() {
        val doc = mapOf<String, Any?>(
            "takenAt" to ts(takenAt),
            "takenAtEpochMillis" to 1_800_000_000_000L,
        )
        assertEquals(takenAt, epochMillisOf(doc, "takenAt", "takenAtEpochMillis"))
    }

    @Test
    fun `senza Timestamp si ripiega sul numero`() {
        assertEquals(takenAt, epochMillisOf(mapOf<String, Any?>("takenAtEpochMillis" to takenAt), "takenAt", "takenAtEpochMillis"))
        // Firestore può restituire un numero intero come Long o come Double
        assertEquals(1_000L, epochMillisOf(mapOf<String, Any?>("takenAtEpochMillis" to 1_000.0), "takenAt", "takenAtEpochMillis"))
        assertEquals(7L, epochMillisOf(mapOf<String, Any?>("takenAtEpochMillis" to 7), "takenAt", "takenAtEpochMillis"))
    }

    @Test
    fun `updatedAt ancora null per una scrittura in attesa del server ripiega sul numero locale`() {
        val doc = mapOf<String, Any?>(
            "updatedAt" to null,
            "updatedAtEpochMillis" to takenAt,
        )
        assertEquals(takenAt, epochMillisOf(doc, "updatedAt", "updatedAtEpochMillis"))
    }

    @Test
    fun `senza nessuna delle due il risultato e null`() {
        assertNull(epochMillisOf(emptyMap(), "takenAt", "takenAtEpochMillis"))
        assertNull(epochMillisOf(mapOf<String, Any?>("takenAt" to "ieri"), "takenAt", "takenAtEpochMillis"))
    }
}
