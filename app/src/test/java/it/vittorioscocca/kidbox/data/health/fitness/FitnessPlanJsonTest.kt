package it.vittorioscocca.kidbox.data.health.fitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il documento del piano fitness è condiviso con iOS: questi test girano su un
 * payload reale scritto dal client iOS (`fitness_plan_ios.json`).
 *
 * Il bug che coprono: Android leggeva `startDateEpochMillis` mentre iOS scrive
 * `startDate` in ISO 8601, quindi il piano arrivava con data d'inizio 1970 —
 * calendario vuoto, tutte e quattro le settimane apparentemente concluse e
 * report della settimana 4 mostrato durante la prima.
 */
class FitnessPlanJsonTest {

    private fun payload(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fitness_plan_ios.json"))
            .bufferedReader().use { it.readText() }

    @Test
    fun `legge il documento scritto da iOS`() {
        val plan = FitnessPlanJson.decode(payload())
        assertNotNull("il piano di iOS deve essere leggibile", plan)
        requireNotNull(plan)

        // 2026-08-30T22:00:00Z = mezzanotte del 31/08 a Roma.
        assertTrue("data d'inizio non letta", plan.startDateEpochMillis > 1_700_000_000_000L)
        assertEquals(4, plan.weeks.size)
        assertEquals(12, plan.allSessions.size)
        assertTrue("le sedute devono avere date reali", plan.allSessions.all { it.dateEpochMillis > 1_700_000_000_000L })

        // Gli enum arrivano in camelCase: se non li riconoscessimo tornerebbero
        // ai default e le scelte dell'utente sparirebbero in silenzio.
        assertEquals(FitnessGoal.TONING, plan.input.goal)
        assertEquals(FitnessExperience.INTERMEDIATE, plan.input.experience)
        assertEquals(FitnessPlace.OUTDOOR, plan.input.place)
        assertTrue(FitnessSport.TENNIS in plan.input.preferredSports)
        assertTrue(FitnessSport.CYCLING in plan.input.preferredSports)
    }

    @Test
    fun `la prima settimana non risulta conclusa`() {
        val plan = requireNotNull(FitnessPlanJson.decode(payload()))
        val completed = FitnessWeeklyReportBuilder.lastCompletedWeekIndex(plan)
        val weeksElapsed = FitnessPlanDates.daysBetween(
            plan.startDateEpochMillis,
            FitnessPlanDates.today(),
        ) / 7
        assertTrue(
            "settimane concluse ($completed) oltre quelle trascorse ($weeksElapsed)",
            (completed ?: 0) <= weeksElapsed,
        )
    }

    @Test
    fun `il formato scritto da Android è rileggibile`() {
        val original = requireNotNull(FitnessPlanJson.decode(payload()))
        val roundTrip = requireNotNull(FitnessPlanJson.decode(FitnessPlanJson.encode(original)))

        assertEquals(original.startDateEpochMillis, roundTrip.startDateEpochMillis)
        assertEquals(original.allSessions.size, roundTrip.allSessions.size)
        assertEquals(original.input.goal, roundTrip.input.goal)
        assertEquals(original.input.preferredSports, roundTrip.input.preferredSports)
        assertEquals(
            original.allSessions.map { it.dateEpochMillis },
            roundTrip.allSessions.map { it.dateEpochMillis },
        )
    }

    @Test
    fun `le date scritte non hanno millisecondi`() {
        // Il decoder di iOS con strategia .iso8601 rifiuta le frazioni di secondo:
        // una data con i millisecondi gli farebbe scartare l'intero piano.
        val plan = requireNotNull(FitnessPlanJson.decode(payload()))
        val encoded = FitnessPlanJson.encode(plan)
        assertTrue(
            "date con millisecondi nel payload",
            Regex("\"[^\"]*\\d\\.\\d{3}Z\"").find(encoded) == null,
        )
    }

    @Test
    fun `un documento senza data d'inizio viene scartato`() {
        // Scartarlo è la difesa che impedisce di sovrascrivere un piano locale
        // valido con uno illeggibile.
        assertNull(FitnessPlanJson.decode("""{"subjectName":"X","weeks":[]}"""))
    }
}
