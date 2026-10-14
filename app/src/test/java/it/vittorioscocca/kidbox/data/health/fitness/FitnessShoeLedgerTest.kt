package it.vittorioscocca.kidbox.data.health.fitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessShoeLedgerTest {

    private val day = 24L * 3600 * 1000
    private val t0 = 1_760_000_000_000L // un giorno qualsiasi

    private fun run(id: String, at: Long, meters: Double?, title: String = "Corsa all'aperto", type: String = "corsa") =
        FitnessSession(
            id = id, dateEpochMillis = at, weekIndex = 1, title = title, activityType = type,
            durationMinutes = 40, status = FitnessSessionStatus.DONE,
            completedAtEpochMillis = at, actualActivityTitle = title, actualDistanceMeters = meters,
        )

    private fun plan(vararg sessions: FitnessSession, logged: List<FitnessLoggedWorkout> = emptyList()) =
        FitnessPlanDocument(
            subjectName = "T", input = FitnessPlanInput(), startDateEpochMillis = t0, summary = "",
            safetyNotes = emptyList(), weeks = listOf(FitnessWeek(1, "", sessions.toList())),
            generatedAtEpochMillis = t0, messageUnitsConsumed = 0, loggedWorkouts = logged,
        )

    private fun shoe(id: String, bought: Long, maxKm: Double = 700.0) =
        FitnessShoe(id = id, childId = "c", brand = id, purchaseDateEpochMillis = bought, maxKm = maxKm)

    @Test
    fun countsOnlyRunsAfterPurchase() {
        val shoes = listOf(shoe("a", t0))
        val doc = plan(run("before", t0 - 5 * day, 10_000.0), run("after", t0 + day, 5_000.0))
        val changed = FitnessShoeLedger.reconcile(shoes, doc)
        assertEquals(setOf("after"), changed.single().runs.keys)
        assertEquals(5.0, changed.single().usedKm, 0.001)
        assertEquals(695.0, changed.single().remainingKm, 0.001)
    }

    @Test
    fun ignoresNonRunsAndMissingDistance() {
        val shoes = listOf(shoe("a", t0 - day))
        val doc = plan(
            run("bike", t0, 20_000.0, title = "Bici", type = "ciclismo"),
            run("nodist", t0, null),
            run("tiny", t0, 3.0),
        )
        assertTrue(FitnessShoeLedger.reconcile(shoes, doc).isEmpty())
    }

    @Test
    fun reconcileIsIdempotentAndKeepsHistory() {
        val doc = plan(run("r1", t0 + day, 5_000.0))
        val first = FitnessShoeLedger.reconcile(listOf(shoe("a", t0)), doc)
        val second = FitnessShoeLedger.reconcile(first, doc)
        assertTrue(second.isEmpty())
        // piano rigenerato senza la corsa: i km restano
        val after = FitnessShoeLedger.reconcile(first, plan())
        assertTrue(after.isEmpty())
        assertEquals(5.0, first.single().usedKm, 0.001)
    }

    @Test
    fun newestNotRetiredShoeGetsTheRun() {
        val old = shoe("old", t0 - 100 * day)
        val new = shoe("new", t0)
        val r = FitnessShoeLedger.Run("r", t0 + day, 5_000.0)
        assertEquals("new", FitnessShoeLedger.owner(r, listOf(old, new))?.id)
        val newRetired = new.copy(retiredAtEpochMillis = t0 + day / 2)
        assertEquals("old", FitnessShoeLedger.owner(r, listOf(old, newRetired))?.id)
        assertNull(FitnessShoeLedger.owner(r, listOf(old.copy(isDeleted = true), newRetired)))
    }

    @Test
    fun manualKmAddsToRunsAndNeverGoesBelowZero() {
        val s = shoe("a", t0).copy(runs = mapOf("r" to 5_000.0), manualKm = 120.0)
        assertEquals(125.0, s.usedKm, 0.001)
        assertEquals(575.0, s.remainingKm, 0.001)
        assertEquals(0.0, s.copy(manualKm = -50.0).usedKm, 0.001)
        // una corsa nuova si somma al valore corretto a mano
        val doc = plan(run("r2", t0 + day, 10_000.0))
        assertEquals(135.0, FitnessShoeLedger.reconcile(listOf(s), doc).single().usedKm, 0.001)
    }

    @Test
    fun loggedWorkoutsCountAndMergeUnionsRuns() {
        val doc = plan(logged = listOf(FitnessLoggedWorkout("w1", t0 + day, "Running", distanceMeters = 8_000.0)))
        val changed = FitnessShoeLedger.reconcile(listOf(shoe("a", t0)), doc).single()
        assertEquals(8.0, changed.usedKm, 0.001)
        val remote = shoe("a", t0).copy(runs = mapOf("w2" to 3_000.0), updatedAtEpochMillis = changed.updatedAtEpochMillis + 1)
        val merged = FitnessShoeLedger.merge(changed, remote)
        assertEquals(setOf("w1", "w2"), merged.runs.keys)
    }
}
