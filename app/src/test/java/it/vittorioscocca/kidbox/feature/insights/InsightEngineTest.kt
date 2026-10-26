package it.vittorioscocca.kidbox.feature.insights

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stessi casi di `KBInsightEngineTests.swift` e `KBInsightNotificationSchedulerTests.swift`. */
class InsightEngineTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(d: Int, h: Int, m: Int, month: Int = 10) =
        LocalDateTime.of(2026, month, d, h, m).atZone(rome).toInstant().toEpochMilli()

    /** Venerdì 23 ottobre 2026, 12:00 a Roma. */
    private val now = at(23, 12, 0)

    private fun todo(
        id: String = "t1", due: Long?, hasTime: Boolean = true, done: Boolean = false,
        taken: Boolean = false, visible: Boolean = true,
    ) = InsightSnapshot.Todo(id, due, hasTime, done, false, taken, visible)

    private fun treatment(
        id: String = "c1", start: Long = at(21, 9, 0), days: Int = 5, longTerm: Boolean = false,
        interval: Int = 0, times: List<String> = listOf("08:00", "20:00"),
    ) = InsightSnapshot.Treatment(id, true, false, longTerm, days, start, null, interval, times)

    private fun eval(s: InsightSnapshot) = InsightEngine.evaluate(s, now, rome)

    // ── To-do ──────────────────────────────────────────────────────────────

    @Test
    fun unassignedTodoFiresDayBeforeAtSameTimeAcrossDst() {
        // Il 25/10 torna l'ora solare: «24 ore prima» delle 9:00 sarebbero le 10:00.
        val c = eval(InsightSnapshot(todos = listOf(todo(due = at(25, 9, 0)))))
        assertEquals(listOf(InsightCandidate(InsightType.TODO_UNASSIGNED_DUE_SOON, "t1", InsightPriority.NORMAL, at(24, 9, 0))), c)
    }

    @Test
    fun withinSixHoursIsHigh() {
        assertEquals(InsightPriority.HIGH, eval(InsightSnapshot(todos = listOf(todo(due = at(23, 17, 0))))).single().priority)
    }

    @Test
    fun todoWithoutTimeDueAtEndOfDay() {
        val c = eval(InsightSnapshot(todos = listOf(todo(due = at(23, 0, 0), hasTime = false)))).single()
        assertEquals(at(23, 0, 0), c.fireAtMillis)
    }

    @Test
    fun skippedWhenTakenDoneHiddenPastFarOrUndated() {
        val s = InsightSnapshot(
            todos = listOf(
                todo("taken", at(24, 9, 0), taken = true),
                todo("done", at(24, 9, 0), done = true),
                todo("hidden", at(24, 9, 0), visible = false),
                todo("past", at(23, 11, 0)),
                todo("far", at(5, 9, 0, month = 11)),
                todo("nodate", null),
            ),
        )
        assertEquals(emptyList<InsightCandidate>(), eval(s))
    }

    // ── Dosi ───────────────────────────────────────────────────────────────

    @Test
    fun missingMorningDoseFiresAfterGrace() {
        assertEquals(
            listOf(InsightCandidate(InsightType.TREATMENT_DOSE_MISSING, "c1@2026-10-23", InsightPriority.HIGH, at(23, 9, 0))),
            eval(InsightSnapshot(treatments = listOf(treatment()))),
        )
    }

    @Test
    fun takenMorningMovesToEvening() {
        val s = InsightSnapshot(
            treatments = listOf(treatment()),
            doseLogs = listOf(InsightSnapshot.DoseLog("c1", 3, 0, taken = true, isDeleted = false)),
        )
        assertEquals(at(23, 21, 0), eval(s).single().fireAtMillis)
    }

    @Test
    fun outsideTreatmentDaysGivesNothing() {
        val ended = treatment(start = at(10, 9, 0), days = 5)
        val future = treatment("c2", start = at(25, 9, 0))
        val skipDay = treatment("c3", start = at(20, 9, 0), days = 10, interval = 2)
        assertEquals(emptyList<InsightCandidate>(), eval(InsightSnapshot(treatments = listOf(ended, future, skipDay))))
    }

    // ── Riconciliazione ────────────────────────────────────────────────────

    @Test
    fun intervalTreatmentOnlyCountsFirstTime() {
        // Una dose ogni 2 giorni (oggi è il giorno 3, offset 2): solo il primo orario.
        val s = InsightSnapshot(
            treatments = listOf(treatment(interval = 2, times = listOf("08:00", "20:00"))),
            doseLogs = listOf(InsightSnapshot.DoseLog("c1", 3, 0, true, false)),
        )
        assertTrue(eval(s).isEmpty())
    }

    @Test
    fun uncoveredSlotsListsOnlyTheNotTakenOnes() {
        val s = InsightSnapshot(
            treatments = listOf(treatment()),
            doseLogs = listOf(InsightSnapshot.DoseLog("c1", 3, 0, true, false)),
        )
        val today = java.time.Instant.ofEpochMilli(now).atZone(rome).toLocalDate()
        val slots = InsightEngine.uncoveredSlots(s.treatments[0], InsightEngine.takenKeys(s), today, rome)
        assertEquals(listOf("20:00"), slots.map { it.time })
    }

    @Test
    fun reconcileOpensResolvesAndKeepsDismissed() {
        val a = InsightCandidate(InsightType.TODO_UNASSIGNED_DUE_SOON, "t1", InsightPriority.NORMAL, now)
        val b = InsightCandidate(InsightType.TODO_UNASSIGNED_DUE_SOON, "t2", InsightPriority.NORMAL, now)
        var (rows, ch) = InsightReconciler.reconcile(emptyList(), listOf(a, b), "f", "u", now)
        assertEquals(2, ch.opened.size)

        // Stessa valutazione: nessun cambiamento.
        val same = InsightReconciler.reconcile(rows, listOf(a, b), "f", "u", now)
        assertTrue(same.second.isEmpty)

        // t2 scartato, poi entrambi spariscono e tornano.
        rows = rows.map { if (it.entityId == "t2") it.mark(InsightState.DISMISSED, now) else it }
        rows = InsightReconciler.reconcile(rows, emptyList(), "f", "u", now).first
        val back = InsightReconciler.reconcile(rows, listOf(a, b), "f", "u", now)
        assertEquals(listOf("f|todo_unassigned_due_soon|t1"), back.second.opened)
        assertEquals(InsightState.DISMISSED, back.first.single { it.entityId == "t2" }.state)
    }

    @Test
    fun recordsSurviveJsonRoundTrip() {
        val rows = InsightReconciler.reconcile(
            emptyList(),
            listOf(InsightCandidate(InsightType.TREATMENT_DOSE_MISSING, "c1@2026-10-23", InsightPriority.HIGH, now)),
            "f", "u", now,
        ).first.map { it.copy(notifiedAtMillis = now + 1) }
        assertEquals(rows, InsightRecord.listFromJson(InsightRecord.listToJson(rows)))
        assertEquals(emptyList<InsightRecord>(), InsightRecord.listFromJson("non json"))
    }

    // ── Consegna ───────────────────────────────────────────────────────────

    private val defaults = InsightPreferences()

    @Test
    fun deliveryRespectsQuietHoursExceptDoses() {
        val todoType = InsightType.TODO_UNASSIGNED_DUE_SOON
        assertEquals(at(24, 9, 0), InsightDelivery.deliveryTime(at(24, 9, 0), todoType, now, defaults, rome))
        assertEquals(now + 5_000, InsightDelivery.deliveryTime(at(22, 9, 0), todoType, now, defaults, rome))
        assertEquals(at(24, 8, 0), InsightDelivery.deliveryTime(at(23, 22, 15), todoType, now, defaults, rome))
        assertEquals(at(24, 8, 0), InsightDelivery.deliveryTime(at(24, 6, 30), todoType, now, defaults, rome))
        assertEquals(at(23, 22, 15), InsightDelivery.deliveryTime(at(23, 22, 15), InsightType.TREATMENT_DOSE_MISSING, now, defaults, rome))
    }

    @Test
    fun digestTodayOrTomorrowAndOutOfQuiet() {
        assertEquals(at(23, 19, 0), InsightDelivery.nextDigest(now, defaults, rome))
        assertEquals(at(24, 19, 0), InsightDelivery.nextDigest(at(23, 20, 0), defaults, rome))
        val early = defaults.copy(quietStartMinutes = 18 * 60, quietEndMinutes = 7 * 60)
        assertEquals(at(24, 7, 0), InsightDelivery.nextDigest(now, early, rome))
    }

    @Test
    fun planDoesNotRepeatDeliveredAndUsesDigest() {
        val rows = InsightReconciler.reconcile(
            emptyList(),
            listOf(
                InsightCandidate(InsightType.TODO_UNASSIGNED_DUE_SOON, "t1", InsightPriority.NORMAL, at(23, 9, 0)),
                InsightCandidate(InsightType.TREATMENT_DOSE_MISSING, "c1@2026-10-23", InsightPriority.HIGH, at(23, 21, 0)),
            ),
            "f", "u", now,
        ).first

        val first = InsightDelivery.plan(rows, "f", defaults, now, rome)
        assertEquals(2, first.notifications.size)
        // Giro dopo la consegna del to-do: non si ripete, la dose resta armata.
        val later = at(23, 12, 1)
        val second = InsightDelivery.plan(first.rows, "f", defaults, later, rome)
        assertEquals(listOf("f|treatment_dose_missing|c1@2026-10-23"), second.notifications.map { it.insightId })

        // Riepilogo serale: il to-do va nel riepilogo, la dose resta puntuale.
        val digest = InsightDelivery.plan(rows, "f", defaults.copy(delivery = InsightPreferences.Delivery.EVENING_DIGEST), now, rome)
        assertEquals(listOf("f|todo_unassigned_due_soon|t1"), digest.digestIds)
        assertEquals(at(23, 19, 0), digest.digestAtMillis)
        assertEquals(listOf("f|treatment_dose_missing|c1@2026-10-23"), digest.notifications.map { it.insightId })
    }

    @Test
    fun preferencesFromFirestoreToleratesBadValues() {
        val p = InsightPreferences(
            delivery = InsightPreferences.Delivery.EVENING_DIGEST, quietStartMinutes = 1320, quietEndMinutes = 450,
            mutedTypes = setOf(InsightType.TREATMENT_DOSE_MISSING), delegateAsk = true,
        )
        assertEquals(p, InsightPreferences.fromFirestore(p.toFirestore()))
        val bad = InsightPreferences.fromFirestore(mapOf("delivery" to "sometimes", "quietStart" to 5000L, "mutedTypes" to listOf("future", "grocery_stale")))
        assertEquals(InsightPreferences.Delivery.IMMEDIATE, bad.delivery)
        assertEquals(21 * 60 + 30, bad.quietStartMinutes)
        assertEquals(setOf(InsightType.GROCERY_STALE), bad.mutedTypes)
        assertFalse(bad.delegateAsk)
    }

    @Test
    fun askRecipientsFollowVisibility() {
        val members = listOf("me", "dad", "nonna")
        assertEquals(listOf("dad", "nonna"), InsightRunner.askRecipients("family", emptyList(), members, "me"))
        assertEquals(listOf("dad"), InsightRunner.askRecipients("members", listOf("me", "dad"), members, "me"))
        assertEquals(emptyList<String>(), InsightRunner.askRecipients("private", emptyList(), members, "me"))
    }

    // ── Pagamenti di casa (stessi casi di iOS) ─────────────────────────────

    private fun payment(day: Int? = null, date: Long? = null) = InsightSnapshot.HousePayment("p1", false, date, day)

    @Test
    fun monthlyPaymentAsksThreeDaysBeforeAtNine() {
        assertEquals(
            listOf(InsightCandidate(InsightType.HOUSE_PAYMENT_DUE, "p1@2026-10-26", InsightPriority.NORMAL, at(23, 9, 0))),
            eval(InsightSnapshot(housePayments = listOf(payment(day = 26)))),
        )
        assertEquals(InsightPriority.HIGH, eval(InsightSnapshot(housePayments = listOf(payment(day = 24)))).single().priority)
    }

    @Test
    fun day31AndPastDaysAndOneOff() {
        assertEquals(
            java.time.LocalDate.of(2026, 11, 30),
            InsightEngine.nextDue(payment(day = 31), java.time.LocalDate.of(2026, 11, 25), rome),
        )
        assertEquals(emptyList<InsightCandidate>(), eval(InsightSnapshot(housePayments = listOf(payment(day = 10)))))
        assertEquals("p1@2026-10-28", eval(InsightSnapshot(housePayments = listOf(payment(day = 1, date = at(28, 0, 0))))).single().entityId)
        assertEquals(emptyList<InsightCandidate>(), eval(InsightSnapshot(housePayments = listOf(payment(date = at(20, 0, 0))))))
    }

    @Test
    fun paymentWithItsTodoIsNotAsked() {
        val id = InsightEngine.paymentTodoId("p1", java.time.LocalDate.of(2026, 10, 26))
        assertEquals("hp-p1-2026-10-26", id)
        assertEquals(emptyList<InsightCandidate>(), eval(InsightSnapshot(housePayments = listOf(payment(day = 26)), todoIds = setOf(id))))
    }
}
